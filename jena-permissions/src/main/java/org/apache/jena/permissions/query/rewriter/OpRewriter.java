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
import java.util.List;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.SecuredItem;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.permissions.SecurityEvaluator.Action;
import org.apache.jena.shared.AuthenticationRequiredException;
import org.apache.jena.shared.ReadDeniedException;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpVisitor;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprFunctionOp;
import org.apache.jena.sparql.expr.ExprList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class rewrites the query by examining each operation in the algebra
 * returned by the Jena SPARQL parser.
 * <p>
 * This implementation inserts security evaluator checks where necessary.
 * </p>
 */
public class OpRewriter implements OpVisitor {
    private static Logger LOG = LoggerFactory.getLogger(OpRewriter.class);
    private OpSequence result;
    private final Node graphIRI;
    private final SecurityEvaluator securityEvaluator;
    // if true the restricted data are silently ignored.
    // default false
    private final boolean silentFail;

    /**
     * Constructor
     *
     * @param securityEvaluator The security evaluator to use
     * @param graphIRI          The IRI for the default graph.
     */
    public OpRewriter(final SecurityEvaluator securityEvaluator, final Node graphIRI) {
        this.securityEvaluator = securityEvaluator;
        this.graphIRI = graphIRI;
        this.silentFail = false;
        reset();
    }

    /**
     * Constructor
     *
     * @param securityEvaluator The security evaluator to use
     * @param graphIRI          The IRI for the default graph.
     */
    public OpRewriter(final SecurityEvaluator securityEvaluator, final String graphIRI) {
        this(securityEvaluator, NodeFactory.createURI(graphIRI));
    }

    /**
     * Add the operation to the result.
     *
     * @param op the operation to add.
     */
    private void addOp(final Op op) {
        result.add(op);
    }

    /**
     * Get the result of the rewrite.
     *
     * @return the resulting operator
     */
    public Op getResult() {
        if (result.size() == 0) {
            return OpNull.create();
        }
        if (result.size() == 1) {
            return result.get(0);
        }
        return result;

    }

    /**
     * Reset the rewriter to the initial state.
     *
     * @return this rewriter for chaining.
     */
    public OpRewriter reset() {
        result = OpSequence.create();
        return this;
    }

    /**
     * Rewrites the subop of op1 and returns the result.
     *
     * @param op1
     * @return the rewritten op.
     */
    private Op rewriteOp1(final Op1 op1) {
        final OpRewriter rewriter = new OpRewriter(securityEvaluator, graphIRI);
        op1.getSubOp().visit(rewriter);
        return rewriter.getResult();
    }

    /**
     * rewrites the left and right parts of the op2 the left part is returned the
     * right part is placed in the rewriter
     *
     * @param op2
     * @param rewriter
     * @return the rewritten op.
     */
    private Op rewriteOp2(final Op2 op2, final OpRewriter rewriter) {
        op2.getLeft().visit(rewriter.reset());
        final Op left = rewriter.getResult();
        op2.getRight().visit(rewriter.reset());
        return left;
    }

    /**
     * rewrite source to dest and returns dest
     *
     * @param source
     * @param dest
     * @return the rewritten op.
     */
    private OpN rewriteOpN(final OpN source, final OpN dest) {
        final OpRewriter rewriter = new OpRewriter(securityEvaluator, graphIRI);
        for (final Op o : source.getElements()) {
            o.visit(rewriter.reset());
            dest.add(rewriter.getResult());
        }
        return dest;
    }

    /**
     * Handles an operation that reads triples the rewriter cannot express as an
     * algebra-level {@code SecuredFunction} filter — a property path, a quad
     * block, an opaque extension, or dataset-name enumeration. Such an operation
     * touches triples that are never bound to query variables, so no per-triple
     * filter can be built for it. If the principal may read every triple of the
     * (concrete) graph the operation is safe and passes through unchanged;
     * otherwise the rewriter cannot prove it only touches readable data and fails
     * closed, exactly as the {@link #visit(OpBGP)} handler does when the graph is
     * unreadable.
     *
     * @param op the unfilterable operation.
     */
    private void passThroughOrDeny(final Op op) {
        final Object principal = securityEvaluator.getPrincipal();
        if (graphIRI != null && !graphIRI.isVariable()
                && securityEvaluator.evaluate(principal, Action.Read, graphIRI, Triple.ANY)) {
            addOp(op);
            return;
        }
        if (silentFail) {
            return;
        }
        throw new ReadDeniedException(SecuredItem.Util.modelPermissionMsg(graphIRI));
    }

    /**
     * Rewrites the graph pattern nested inside an expression list. SPARQL
     * {@code FILTER EXISTS} / {@code NOT EXISTS} carry their pattern as an
     * {@link ExprFunctionOp}; that inner {@code Op} is executed directly (see
     * {@code ExprFunctionOp.eval}), so it must be rewritten too — otherwise an
     * existence test over denied data is evaluated unfiltered. Every other
     * expression is returned unchanged.
     *
     * @param exprs the expression list to rewrite (may be {@code null}).
     * @return the rewritten list, or the original instance if nothing changed.
     */
    private ExprList rewriteExprs(final ExprList exprs) {
        if (exprs == null || exprs.isEmpty()) {
            return exprs;
        }
        boolean changed = false;
        final ExprList rewritten = new ExprList();
        for (final Expr expr : exprs.getList()) {
            if (expr instanceof ExprFunctionOp) {
                final ExprFunctionOp funcOp = (ExprFunctionOp) expr;
                final OpRewriter subRewriter = new OpRewriter(securityEvaluator, graphIRI);
                funcOp.getGraphPattern().visit(subRewriter);
                rewritten.add(funcOp.copy(new ExprList(funcOp.getArgs()), subRewriter.getResult()));
                changed = true;
            } else {
                rewritten.add(expr);
            }
        }
        return changed ? rewritten : exprs;
    }

    /**
     * rewrites the subop of assign.
     */
    @Override
    public void visit(final OpAssign opAssign) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpAssign");
        }
        addOp(OpAssign.assign(rewriteOp1(opAssign), opAssign.getVarExprList()));
    }

    @Override
    public void visit(final OpBGP opBGP) throws ReadDeniedException, AuthenticationRequiredException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpBGP");
        }
        Object principal = securityEvaluator.getPrincipal();
        // A concrete graph can be authorized up front. A variable graph (from
        // GRAPH ?g { ... }) is only known per row, so the graph-level shortcuts
        // below are skipped and the per-row SecuredFunction — which resolves the
        // graph node from each binding — is always inserted.
        final boolean variableGraph = graphIRI == null || graphIRI.isVariable();
        if (!variableGraph && !securityEvaluator.evaluate(principal, Action.Read, graphIRI)) {
            if (silentFail) {
                return;
            }
            throw new ReadDeniedException(SecuredItem.Util.modelPermissionMsg(graphIRI));
        }

        // if the graph is concrete and the user can read any triple just add the opBGP
        if (!variableGraph && securityEvaluator.evaluate(principal, Action.Read, graphIRI, Triple.ANY)) {
            addOp(opBGP);
        } else {
            // add security filtering to the resulting triples
            final List<Triple> newBGP = new ArrayList<>(opBGP.getPattern().getList());
            // create the security function (it derives its own variables).
            final SecuredFunction secFunc = new SecuredFunction(graphIRI, securityEvaluator, newBGP);
            // create the filter
            Op filter = OpFilter.filter(secFunc, new OpBGP(BasicPattern.wrap(newBGP)));
            // add the filter
            addOp(filter);
        }
    }

    @Override
    public void visit(OpLateral opLateral) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpLateral");
        }
        final OpRewriter rewriter = new OpRewriter(securityEvaluator, graphIRI);
        addOp(OpLateral.create(rewriteOp2(opLateral, rewriter), rewriter.getResult()));
    }

    @Override
    public void visit(OpSemiJoin opSemiJoin) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpSemiJoin");
        }
        final OpRewriter rewriter = new OpRewriter(securityEvaluator, graphIRI);
        addOp(OpSemiJoin.create(rewriteOp2(opSemiJoin, rewriter), rewriter.getResult()));
    }

    @Override
    public void visit(OpAntiJoin opAntiJoin) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpAntiJoin");
        }
        final OpRewriter rewriter = new OpRewriter(securityEvaluator, graphIRI);
        addOp(OpAntiJoin.create(rewriteOp2(opAntiJoin, rewriter), rewriter.getResult()));
    }

    /**
     * Rewrite left and right
     */
    @Override
    public void visit(final OpConditional opCondition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpConditional");
        }
        final OpRewriter rewriter = new OpRewriter(securityEvaluator, graphIRI);
        addOp(new OpConditional(rewriteOp2(opCondition, rewriter), rewriter.getResult()));
    }

    /**
     * returns the dsNames
     */
    @Override
    public void visit(final OpDatasetNames dsNames) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpDatasetName");
        }
        // Enumerates graph names — a metadata read that cannot be filtered
        // per-triple. Pass through only when the principal can read everything.
        passThroughOrDeny(dsNames);
    }

    /**
     * Rewrite sequence elements
     */
    @Override
    public void visit(final OpDisjunction opDisjunction) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpDisjunction");
        }
        addOp(rewriteOpN(opDisjunction, OpDisjunction.create()));
    }

    /**
     * rewrites the subop of distinct
     */
    @Override
    public void visit(final OpDistinct opDistinct) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpDistinct");
        }
        addOp(new OpDistinct(rewriteOp1(opDistinct)));
    }

    /**
     * Returns the Ext
     */
    @Override
    public void visit(final OpExt opExt) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpExt");
        }
        // Opaque extension algebra the rewriter cannot descend into; treat it as
        // an unfilterable read and fail closed unless the principal reads all.
        passThroughOrDeny(opExt);
    }

    /**
     * rewrites the subop of extend.
     */
    @Override
    public void visit(final OpExtend opExtend) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpExtend");
        }
        addOp(OpExtend.extend(rewriteOp1(opExtend), opExtend.getVarExprList()));
    }

    /**
     * rewrites the subop of unfold.
     */
    @Override
    public void visit(final OpUnfold opUnfold) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpUnfold");
        }
        Op subOp = rewriteOp1(opUnfold);
        OpUnfold opUnfold2 = new OpUnfold(subOp, opUnfold.getExpr(), opUnfold.getVar1(), opUnfold.getVar2());
        addOp(opUnfold2);
    }

    /**
     * rewrites the subop of filter.
     */
    @Override
    public void visit(final OpFilter opFilter) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpFilter");
        }
        // Rewrite both the sub-op and any EXISTS/NOT EXISTS pattern carried in the
        // filter expressions, so an existence test over denied data is filtered.
        addOp(OpFilter.filterBy(rewriteExprs(opFilter.getExprs()), rewriteOp1(opFilter)));
    }

    /**
     * rewrites the subop of graph.
     */
    @Override
    public void visit(final OpGraph opGraph) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpGraph");
        }
        final Node graphNode = opGraph.getNode();
        if (graphNode.isVariable() && opGraph.getSubOp() instanceof OpBGP) {
            // GRAPH ?g { <bgp> }: ARQ binds ?g by joining it onto the pattern's
            // solutions *after* the pattern runs, so ?g is deliberately not visible
            // inside the pattern and a filter placed there could not resolve it.
            // Instead wrap the whole graph in a SecuredFunction filter evaluated on
            // the graph's output, where ?g and the pattern variables are all bound;
            // the function resolves ?g per row and authorizes each triple against
            // that concrete graph. ?g is declared among the function's variables so
            // the optimizer keeps the filter above the graph.
            final List<Triple> triples = new ArrayList<>(((OpBGP) opGraph.getSubOp()).getPattern().getList());
            final SecuredFunction secFunc = new SecuredFunction(graphNode, securityEvaluator, triples);
            addOp(OpFilter.filter(secFunc, new OpGraph(graphNode, opGraph.getSubOp())));
            return;
        }
        // Concrete graph (checks resolve directly), or a complex sub-pattern under a
        // variable graph (recursive rewrite: a fully-readable principal passes
        // through, a restricted one fails closed since the inner filters cannot
        // resolve the hidden graph variable).
        final OpRewriter rewriter = new OpRewriter(securityEvaluator, graphNode);
        opGraph.getSubOp().visit(rewriter);
        addOp(new OpGraph(graphNode, rewriter.getResult()));
    }

    /**
     * rewrites the subop of group.
     */
    @Override
    public void visit(final OpGroup opGroup) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpGroup");
        }
        addOp(OpGroup.create(rewriteOp1(opGroup), opGroup.getGroupVars(), opGroup.getAggregators()));
    }

    /**
     * Parses the joins and recursively calls the left and right parts
     */
    @Override
    public void visit(final OpJoin opJoin) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpJoin");
        }
        final OpRewriter rewriter = new OpRewriter(securityEvaluator, graphIRI);
        addOp(OpJoin.create(rewriteOp2(opJoin, rewriter), rewriter.getResult()));
    }

    /**
     * returns the label
     */
    @Override
    public void visit(final OpLabel opLabel) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpLabel");
        }
        addOp(opLabel);
    }

    /**
     * Parses the joins and recursively calls the left and right parts
     */
    @Override
    public void visit(final OpLeftJoin opLeftJoin) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpLeftJoin");
        }
        final OpRewriter rewriter = new OpRewriter(securityEvaluator, graphIRI);
        addOp(OpLeftJoin.create(rewriteOp2(opLeftJoin, rewriter), rewriter.getResult(),
                rewriteExprs(opLeftJoin.getExprs())));
    }

    /**
     * rewrites the subop of list.
     */
    @Override
    public void visit(final OpList opList) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpList");
        }
        addOp(new OpList(rewriteOp1(opList)));
    }

    /**
     * Rewrite left and right
     */
    @Override
    public void visit(final OpMinus opMinus) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpMinus");
        }
        final OpRewriter rewriter = new OpRewriter(securityEvaluator, graphIRI);
        addOp(OpMinus.create(rewriteOp2(opMinus, rewriter), rewriter.getResult()));
    }

    /**
     * returns the null
     */
    @Override
    public void visit(final OpNull opNull) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpNull");
        }
        addOp(opNull);
    }

    /**
     * rewrites the subop of order.
     */
    @Override
    public void visit(final OpOrder opOrder) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpOrder");
        }
        addOp(new OpOrder(rewriteOp1(opOrder), opOrder.getConditions()));
    }

    /**
     * Returns the path
     */
    @Override
    public void visit(final OpPath opPath) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpPath");
        }
        // A property path traverses intermediate triples that are never bound to
        // query variables, so a per-triple Read filter cannot be built for it.
        // Pass through only when the principal reads the whole graph.
        passThroughOrDeny(opPath);
    }

    /**
     * Returns the procedure or denies.
     */
    @Override
    public void visit(final OpProcedure opProc) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpProc");
        }
        // M3: a procedure is opaque executable logic with unconstrained access
        // to the dataset; rewriting only its sub-op (as this used to do) leaves
        // whatever the procedure itself reads and binds outside every
        // SecuredFunction filter. Like OpExt, it cannot be filtered per-triple,
        // so pass it through only for a principal that may read every triple.
        passThroughOrDeny(opProc);
    }

    /**
     * rewrites the subop of project.
     */
    @Override
    public void visit(final OpProject opProject) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpProject");
        }
        addOp(new OpProject(rewriteOp1(opProject), opProject.getVars()));
    }

    /**
     * Returns the propFunc or denies.
     */
    @Override
    public void visit(final OpPropFunc opPropFunc) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpPropFunc");
        }
        // M3: a property function reads graph data of its own choosing (e.g.
        // list:member walks rdf:first/rdf:rest chains) and binds the results
        // directly, so those reads are never routed through a per-triple
        // SecuredFunction filter; rewriting only the sub-op (as this used to
        // do) secures nothing about the function itself. Fail closed unless
        // the principal may read the whole graph, exactly as OpPath does.
        // (Note: on the live SecuredQueryEngine path property functions are
        // still plain BGP triples when the rewriter runs — ARQ extracts them
        // in the optimizer afterwards, where they execute against the secured
        // graph proxy and its Read-filtered find(). This handler matters for
        // algebra that arrives already transformed.)
        passThroughOrDeny(opPropFunc);
    }

    /**
     * Returns the quad
     */
    @Override
    public void visit(final OpQuad opQuad) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpQuad");
        }
        // Quad-form triple access: the rewriter only builds triple-level filters,
        // so fail closed under restriction rather than read the quad unfiltered.
        passThroughOrDeny(opQuad);
    }

    /**
     * Returns the quadpattern
     */
    @Override
    public void visit(final OpQuadPattern quadPattern) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpQuadPattern");
        }
        // Quad-form BGP: cannot be filtered per-triple here, so fail closed under
        // restriction (see passThroughOrDeny).
        passThroughOrDeny(quadPattern);
    }

    /**
     * rewrites the subop of reduced.
     */
    @Override
    public void visit(final OpReduced opReduced) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpReduced");
        }
        addOp(OpReduced.create(rewriteOp1(opReduced)));
    }

    /**
     * Rewrite sequence elements
     */
    @Override
    public void visit(final OpSequence opSequence) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpSequence");
        }
        addOp(rewriteOpN(opSequence, OpSequence.create()));
    }

    /**
     * returns the service
     */
    @Override
    public void visit(final OpService opService) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting opService");
        }
        // SERVICE targets a remote endpoint governed by its own access control,
        // not this evaluator's local triple permissions, so there is nothing
        // local to filter — pass it through unchanged.
        addOp(opService);
    }

    /**
     * rewrites the subop of slice
     *
     * This also handles the limit case
     */
    @Override
    public void visit(final OpSlice opSlice) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpSlice");
        }
        // Recurse into the sub-op so LIMIT/OFFSET queries still get their
        // SecuredFunction filters (every other OpModifier already recurses).
        addOp(new OpSlice(rewriteOp1(opSlice), opSlice.getStart(), opSlice.getLength()));
    }

    /**
     * returns the table
     */
    @Override
    public void visit(final OpTable opTable) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpTable");
        }
        addOp(opTable);
    }

    /**
     * rewrites the subop of top.
     */
    @Override
    public void visit(final OpTopN opTop) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpTop");
        }
        addOp(new OpTopN(rewriteOp1(opTop), opTop.getLimit(), opTop.getConditions()));
    }

    /**
     * Converts to BGP
     */
    @Override
    public void visit(final OpTriple opTriple) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpTriple");
        }
        visit(opTriple.asBGP());
    }

    /**
     * Rewrite left and right
     */
    @Override
    public void visit(final OpUnion opUnion) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpUnion");
        }
        final OpRewriter rewriter = new OpRewriter(securityEvaluator, graphIRI);
        addOp(OpUnion.create(rewriteOp2(opUnion, rewriter), rewriter.getResult()));
    }

    @Override
    public void visit(OpQuadBlock quadBlock) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting visiting OpQuadBlock");
        }
        // Quad-form BGP block: same handling as OpQuadPattern.
        passThroughOrDeny(quadBlock);
    }
}
