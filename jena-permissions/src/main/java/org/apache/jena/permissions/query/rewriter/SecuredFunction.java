/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jena.permissions.query.rewriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.permissions.SecurityEvaluator.Action;
import org.apache.jena.shared.AuthenticationRequiredException;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.expr.*;
import org.apache.jena.sparql.function.FunctionEnv;
import org.apache.jena.sparql.graph.NodeTransform;

public class SecuredFunction extends ExprFunctionN {
    private final SecurityEvaluator securityEvaluator;
    private final List<Node> variables;
    private final List<Triple> bgp;
    private final Node graphIRI;

    private static ExprList createArgs(List<Node> variables) {
        ExprList retval = new ExprList();
        for (Node n : variables) {
            retval.add(new ExprVar(n));
        }
        return retval;
    }

    /**
     * Builds a security filter for a basic graph pattern.
     *
     * @param graphIRI          the graph the pattern is matched against. May be a
     *                          variable (from {@code GRAPH ?g { ... }}), in which
     *                          case it is resolved from each row's binding at
     *                          evaluation time and is declared among the
     *                          function's variables so ARQ keeps the filter above
     *                          the graph, where the variable is bound.
     * @param securityEvaluator the evaluator to consult.
     * @param bgp               the triples to authorize.
     */
    public SecuredFunction(final Node graphIRI, final SecurityEvaluator securityEvaluator, final List<Triple> bgp) {
        this(graphIRI, securityEvaluator, bgp, deriveVariables(bgp, graphIRI));
    }

    private SecuredFunction(final Node graphIRI, final SecurityEvaluator securityEvaluator, final List<Triple> bgp,
            final List<Node> variables) {
        super(String.format("<java:%s>", SecuredFunction.class.getName()), createArgs(variables));
        this.securityEvaluator = securityEvaluator;
        this.variables = variables;
        this.bgp = bgp;
        this.graphIRI = graphIRI;
    }

    /**
     * Derives the free variables the function reads: every variable in the
     * triples, plus the graph node when it too is a variable.
     */
    private static List<Node> deriveVariables(final List<Triple> bgp, final Node graphIRI) {
        final List<Node> variables = registerVariables(bgp);
        if (graphIRI != null && graphIRI.isVariable() && !variables.contains(graphIRI)) {
            variables.add(graphIRI);
        }
        return variables;
    }

    private boolean checkAccess(Binding values) throws AuthenticationRequiredException {
        final Object principal = securityEvaluator.getPrincipal();
        // Resolve the graph node against the binding. For GRAPH ?g { ... } the
        // graphIRI is a variable that is only bound per row; without a concrete
        // graph no read decision can be made, so fail closed.
        final Node effectiveGraph = resolveNode(graphIRI, values);
        if (effectiveGraph == null || effectiveGraph.isVariable()) {
            return false;
        }
        for (final Triple t : bgp) {
            final Triple secT = resolveTriple(t, values);
            if (secT == null) {
                // A variable in this triple is unbound for the current row, so the
                // pattern did not match and there is no concrete triple to
                // authorize. Skip it rather than build a Triple with a null node.
                continue;
            }
            if (!securityEvaluator.evaluate(principal, Action.Read, effectiveGraph, secT)) {
                return false;
            }
        }
        return true;
    }

    private Triple resolveTriple(final Triple t, final Binding values) {
        final Node s = resolveNode(t.getSubject(), values);
        final Node p = resolveNode(t.getPredicate(), values);
        final Node o = resolveNode(t.getObject(), values);
        if (s == null || p == null || o == null) {
            return null;
        }
        return Triple.create(s, p, o);
    }

    /**
     * Resolves a pattern node against a binding: a variable is looked up (and may
     * be {@code null} when unbound), a concrete node is returned unchanged.
     */
    private static Node resolveNode(final Node n, final Binding values) {
        if (n != null && n.isVariable()) {
            return values.get(Var.alloc(n));
        }
        return n;
    }

    @Override
    public Expr copySubstitute(Binding binding) {
        // Bake the binding's values into the captured pattern so a substitution
        // pushed in by the engine/optimizer is honored instead of silently
        // dropped (the rewrite runs before ARQ optimization).
        return applyNodeTransform(node -> {
            if (node != null && node.isVariable()) {
                final Node bound = binding.get(Var.alloc(node));
                if (bound != null) {
                    return bound;
                }
            }
            return node;
        });
    }

    @Override
    public Expr applyNodeTransform(NodeTransform transform) {
        // Rebuild the captured graph node and BGP under the transform (e.g. a
        // variable rename) so the checks keep referring to the live variables
        // rather than the pre-transform names.
        final Node newGraphIRI = graphIRI == null ? null : transform.apply(graphIRI);
        final List<Triple> newBgp = new ArrayList<>(bgp.size());
        for (final Triple t : bgp) {
            newBgp.add(Triple.create(transform.apply(t.getSubject()), transform.apply(t.getPredicate()),
                    transform.apply(t.getObject())));
        }
        return new SecuredFunction(newGraphIRI, securityEvaluator, newBgp);
    }

    @Override
    public void visit(ExprVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public NodeValue eval(List<NodeValue> args) {
        // Never reached: evalSpecial(Binding, FunctionEnv) short-circuits the
        // per-argument evaluation path below. Fail closed if that ever changes so
        // an unfiltered row can never slip through (and never return null, which
        // would NPE the filter).
        return NodeValue.FALSE;
    }

    @Override
    public Expr copy(ExprList newArgs) {
        // The declared args mirror `variables` positionally and hold no state
        // beyond them, so reflect any rename/substitution the optimizer pushed
        // through the args back into the captured pattern.
        if (newArgs == null || newArgs.size() != variables.size()) {
            return this;
        }
        final Map<Node, Node> renames = new HashMap<>();
        for (int i = 0; i < variables.size(); i++) {
            final Expr arg = newArgs.get(i);
            final Node mapped;
            if (arg.isVariable()) {
                mapped = arg.asVar();
            } else if (arg.isConstant()) {
                mapped = arg.getConstant().asNode();
            } else {
                return this; // unexpected arg shape — keep the original
            }
            if (!mapped.equals(variables.get(i))) {
                renames.put(variables.get(i), mapped);
            }
        }
        if (renames.isEmpty()) {
            return this;
        }
        return applyNodeTransform(node -> renames.getOrDefault(node, node));
    }

    @Override
    protected NodeValue evalSpecial(Binding binding, FunctionEnv env) {
        return NodeValue.booleanReturn(checkAccess(binding));
    }

    /**
     * Registers every distinct variable appearing in the triples, preserving
     * their first-seen order — the same registration the {@link OpRewriter}
     * performs when it first builds the function.
     */
    private static List<Node> registerVariables(final List<Triple> bgp) {
        final List<Node> variables = new ArrayList<>();
        for (final Triple t : bgp) {
            registerVariable(t.getSubject(), variables);
            registerVariable(t.getPredicate(), variables);
            registerVariable(t.getObject(), variables);
        }
        return variables;
    }

    private static void registerVariable(final Node n, final List<Node> variables) {
        if (n != null && n.isVariable() && !variables.contains(n)) {
            variables.add(n);
        }
    }

}
