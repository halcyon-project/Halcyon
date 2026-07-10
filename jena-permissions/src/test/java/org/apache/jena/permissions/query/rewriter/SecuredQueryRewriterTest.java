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

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.shared.ReadDeniedException;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpAntiJoin;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpDatasetNames;
import org.apache.jena.sparql.algebra.op.OpLateral;
import org.apache.jena.sparql.algebra.op.OpQuadPattern;
import org.apache.jena.sparql.algebra.op.OpSemiJoin;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.QueryIterator;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingFactory;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.graph.NodeTransform;
import org.junit.Assert;
import org.junit.Test;

/**
 * Regression tests for the SPARQL rewriter findings Q1&ndash;Q7.
 * <p>
 * The pre-existing {@code QueryEngineTest}/{@code DataSetTest} run queries over a
 * self-filtering {@code SecuredModel}, whose {@code find()} already hides
 * unreadable triples &mdash; so they pass even when the rewriter inserts no
 * filters at all. These tests therefore <b>isolate the rewriter</b>: they run the
 * <em>rewritten algebra</em> over a plain, non-secured base graph/dataset, where
 * only the rewriter's own {@code SecuredFunction} filters can enforce security.
 * Each restricted case would leak (or crash) against the pre-fix rewriter.
 */
public class SecuredQueryRewriterTest {

    private static final String NS = "http://example.com/";
    private static final Property P = ResourceFactory.createProperty(NS + "p");
    private static final Property KNOWS = ResourceFactory.createProperty(NS + "knows");
    private static final Property SECRET = ResourceFactory.createProperty(NS + "secret");
    /** Stand-in for the default graph node the engine hands the top-level rewriter. */
    private static final Node DFT = NodeFactory.createURI("urn:x-arq:DefaultGraph");

    /**
     * Allows every action except Read, which is decided per (graph, triple) by a
     * caller-supplied predicate. A wildcard (ANY) Read returns {@code canReadAny},
     * so a restricting instance ({@code canReadAny == false}) drives the rewriter
     * down its filtering path instead of the "read everything" fast path.
     */
    private static final class RuleEvaluator implements SecurityEvaluator {
        private final boolean canReadAny;
        private final BiPredicate<Node, Triple> canRead;

        RuleEvaluator(final boolean canReadAny, final BiPredicate<Node, Triple> canRead) {
            this.canReadAny = canReadAny;
            this.canRead = canRead;
        }

        @Override
        public boolean evaluate(final Object principal, final Action action, final Node graphIRI) {
            return true;
        }

        @Override
        public boolean evaluate(final Object principal, final Action action, final Node graphIRI, final Triple triple) {
            if (action != Action.Read) {
                return true;
            }
            if (triple.getSubject() == Node.ANY || triple.getPredicate() == Node.ANY
                    || triple.getObject() == Node.ANY) {
                return canReadAny;
            }
            return canRead.test(graphIRI, triple);
        }

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public boolean isPrincipalAuthenticated(final Object principal) {
            return true;
        }
    }

    private static final SecurityEvaluator ALLOW_ALL = new RuleEvaluator(true, (g, t) -> true);

    // ---- helpers -----------------------------------------------------------

    /** Compile a query string to its algebra. */
    private static Op compile(final String query) {
        return Algebra.compile(QueryFactory.create(query));
    }

    /** Rewrite an algebra with the given evaluator against a concrete default graph. */
    private static Op rewrite(final Op op, final SecurityEvaluator eval) {
        return rewrite(op, eval, DFT);
    }

    private static Op rewrite(final Op op, final SecurityEvaluator eval, final Node graphNode) {
        final OpRewriter rewriter = new OpRewriter(eval, graphNode);
        op.visit(rewriter);
        return rewriter.getResult();
    }

    private static List<Binding> exec(final Op op, final Model model) {
        return drain(Algebra.exec(op, model.getGraph()));
    }

    private static List<Binding> exec(final Op op, final DatasetGraph dsg) {
        return drain(Algebra.exec(op, dsg));
    }

    private static List<Binding> drain(final QueryIterator qIter) {
        final List<Binding> out = new ArrayList<>();
        try {
            while (qIter.hasNext()) {
                out.add(qIter.next());
            }
        } finally {
            qIter.close();
        }
        return out;
    }

    // ---- Q1: OpSlice / LIMIT -----------------------------------------------

    @Test
    public void q1_limitStillFiltersUnreadableTriples() {
        final Model base = ModelFactory.createDefaultModel();
        base.add(base.createResource(NS + "a"), P, "keep");
        base.add(base.createResource(NS + "b"), P, "secret");
        final Triple denied = Triple.create(NodeFactory.createURI(NS + "b"), P.asNode(),
                base.createLiteral("secret").asNode());

        final Op secured = rewrite(compile("SELECT * WHERE { ?s ?p ?o } LIMIT 100"),
                new RuleEvaluator(false, (g, t) -> !t.equals(denied)));

        // Over the plain base graph, only the rewriter's filter can hide the
        // denied triple. Pre-fix OpSlice did not recurse -> no filter -> 2 rows.
        final List<Binding> rows = exec(secured, base);
        Assert.assertEquals("LIMIT query must still be filtered", 1, rows.size());
        Assert.assertEquals("the denied triple is filtered, the readable one survives", "keep",
                rows.get(0).get(Var.alloc("o")).getLiteralLexicalForm());
    }

    // ---- Q2: OpPath --------------------------------------------------------

    @Test
    public void q2_propertyPathFailsClosedUnderRestriction() {
        try {
            rewrite(compile("SELECT * WHERE { ?s <" + P.getURI() + ">+ ?o }"),
                    new RuleEvaluator(false, (g, t) -> true));
            Assert.fail("a property path under triple-level restriction must fail closed");
        } catch (final ReadDeniedException expected) {
            // correct: paths cannot be filtered per-triple, so the rewriter denies.
        }
    }

    @Test
    public void q2_propertyPathPassesWhenFullyReadable() {
        final Model base = ModelFactory.createDefaultModel();
        base.add(base.createResource(NS + "a"), P, base.createResource(NS + "b"));
        base.add(base.createResource(NS + "b"), P, base.createResource(NS + "c"));

        final Op secured = rewrite(compile("SELECT * WHERE { ?s <" + P.getURI() + ">+ ?o }"), ALLOW_ALL);
        // a->b, a->c, b->c
        Assert.assertEquals("fully-readable path passes through and evaluates", 3, exec(secured, base).size());
    }

    // ---- Q3: GRAPH ?g ------------------------------------------------------

    @Test
    public void q3_graphVariableResolvesPerRow() {
        final String g1 = NS + "g1";
        final String g2 = NS + "g2";
        final Dataset ds = DatasetFactory.create();
        final Model m1 = ModelFactory.createDefaultModel();
        m1.add(m1.createResource(NS + "s"), P, "v");
        final Model m2 = ModelFactory.createDefaultModel();
        m2.add(m2.createResource(NS + "s"), P, "v");
        ds.addNamedModel(g1, m1);
        ds.addNamedModel(g2, m2);

        final Triple shared = Triple.create(NodeFactory.createURI(NS + "s"), P.asNode(),
                m1.createLiteral("v").asNode());
        // Deny the (identical) triple only in g1. The rewriter must resolve ?g to
        // the concrete graph per row; the pre-fix code passed the unbound Var as
        // the security node, so isURI() was false and g1's row leaked.
        final RuleEvaluator eval = new RuleEvaluator(false,
                (g, t) -> !(g.isURI() && g.getURI().equals(g1) && t.equals(shared)));

        final Op secured = rewrite(compile("SELECT ?g ?s ?p ?o WHERE { GRAPH ?g { ?s ?p ?o } }"), eval);
        final List<Binding> rows = exec(secured, ds.asDatasetGraph());

        Assert.assertEquals("only the readable named graph's row survives", 1, rows.size());
        Assert.assertEquals(g2, rows.get(0).get(Var.alloc("g")).getURI());
    }

    // ---- Q4: FILTER EXISTS -------------------------------------------------

    @Test
    public void q4_filterExistsPatternIsFiltered() {
        final Model base = ModelFactory.createDefaultModel();
        base.add(base.createResource(NS + "a"), KNOWS, base.createResource(NS + "b"));
        base.add(base.createResource(NS + "b"), SECRET, base.createResource(NS + "c"));
        final Triple secret = Triple.create(NodeFactory.createURI(NS + "b"), SECRET.asNode(),
                NodeFactory.createURI(NS + "c"));

        final Op secured = rewrite(
                compile("SELECT ?s ?o WHERE { ?s <" + KNOWS.getURI() + "> ?o . "
                        + "FILTER EXISTS { ?o <" + SECRET.getURI() + "> ?x } }"),
                new RuleEvaluator(false, (g, t) -> !t.equals(secret)));

        // The EXISTS pattern touches only the denied secret triple. Pre-fix, its
        // inner Op was not rewritten, so EXISTS matched and the row leaked.
        Assert.assertEquals("EXISTS over denied data must be filtered", 0, exec(secured, base).size());
    }

    @Test
    public void q4_filterExistsSurvivesWhenReadable() {
        final Model base = ModelFactory.createDefaultModel();
        base.add(base.createResource(NS + "a"), KNOWS, base.createResource(NS + "b"));
        base.add(base.createResource(NS + "b"), SECRET, base.createResource(NS + "c"));

        final Op secured = rewrite(
                compile("SELECT ?s ?o WHERE { ?s <" + KNOWS.getURI() + "> ?o . "
                        + "FILTER EXISTS { ?o <" + SECRET.getURI() + "> ?x } }"),
                ALLOW_ALL);
        Assert.assertEquals("readable EXISTS keeps the row", 1, exec(secured, base).size());
    }

    // ---- Q5: unfilterable ops fail closed under restriction ----------------

    @Test
    public void q5_datasetNamesFailsClosedUnderRestriction() {
        final OpDatasetNames op = new OpDatasetNames(Var.alloc("g"));
        assertDeniedUnderRestriction(op);
        assertPassesWhenFullyReadable(op);
    }

    @Test
    public void q5_quadPatternFailsClosedUnderRestriction() {
        final BasicPattern bp = new BasicPattern();
        bp.add(Triple.create(Var.alloc("s"), P.asNode(), Var.alloc("o")));
        final OpQuadPattern op = new OpQuadPattern(NodeFactory.createURI(NS + "g"), bp);
        assertDeniedUnderRestriction(op);
        assertPassesWhenFullyReadable(op);
    }

    private void assertDeniedUnderRestriction(final Op op) {
        try {
            rewrite(op, new RuleEvaluator(false, (g, t) -> true));
            Assert.fail("unfilterable op must fail closed when the principal cannot read all triples");
        } catch (final ReadDeniedException expected) {
            // correct
        }
    }

    private void assertPassesWhenFullyReadable(final Op op) {
        Assert.assertSame("unfilterable op passes through when the principal reads all", op, rewrite(op, ALLOW_ALL));
    }

    // ---- Q6: OpSemiJoin / OpAntiJoin keep their operator -------------------

    @Test
    public void q6_semiJoinStaysSemiJoin() {
        final Op semi = OpSemiJoin.create(bgp("s", "o"), bgp("o", "x"));
        Assert.assertTrue(semi instanceof OpSemiJoin);
        final Op result = rewrite(semi, ALLOW_ALL);
        Assert.assertTrue("semi-join must not be rewritten to a lateral join", result instanceof OpSemiJoin);
        Assert.assertFalse(result instanceof OpLateral);
    }

    @Test
    public void q6_antiJoinStaysAntiJoin() {
        final Op anti = OpAntiJoin.create(bgp("s", "o"), bgp("o", "x"));
        Assert.assertTrue(anti instanceof OpAntiJoin);
        final Op result = rewrite(anti, ALLOW_ALL);
        Assert.assertTrue("anti-join (a negation) must not become a lateral join", result instanceof OpAntiJoin);
        Assert.assertFalse(result instanceof OpLateral);
    }

    private static OpBGP bgp(final String subj, final String obj) {
        final BasicPattern bp = new BasicPattern();
        bp.add(Triple.create(Var.alloc(subj), P.asNode(), Var.alloc(obj)));
        return new OpBGP(bp);
    }

    // ---- Q7: SecuredFunction robustness ------------------------------------

    @Test
    public void q7_unboundTripleVariableDoesNotThrow() {
        final Var s = Var.alloc("s");
        final List<Triple> pattern = triples(Triple.create(s, P.asNode(), NodeFactory.createLiteralString("v")));
        // Deny everything: if the triple were resolved it would be denied.
        final SecuredFunction f = new SecuredFunction(NodeFactory.createURI(NS + "g"),
                new RuleEvaluator(false, (g, t) -> false), pattern);

        // ?s is unbound: the pre-fix code built Triple.create(null, ...) -> NPE.
        final NodeValue nv = f.evalSpecial(BindingFactory.empty(), null);
        Assert.assertNotNull(nv);
        Assert.assertTrue("an unmatched (unbound) pattern is skipped, not denied", nv.getBoolean());
    }

    @Test
    public void q7_unboundGraphVariableFailsClosed() {
        final Var g = Var.alloc("g");
        final Var s = Var.alloc("s");
        final List<Triple> pattern = triples(Triple.create(s, P.asNode(), NodeFactory.createLiteralString("v")));
        final SecuredFunction f = new SecuredFunction(g, ALLOW_ALL, pattern);

        // ?s bound, graph ?g unbound -> no concrete graph -> deny (fail closed).
        final NodeValue nv = f.evalSpecial(BindingFactory.binding(s, NodeFactory.createURI(NS + "x")), null);
        Assert.assertFalse("an unbound graph variable must fail closed", nv.getBoolean());
    }

    @Test
    public void q7_applyNodeTransformRewritesCapturedPattern() {
        final Var s = Var.alloc("s");
        final Var x = Var.alloc("x");
        final Node deniedSubj = NodeFactory.createURI(NS + "denied");
        final Triple deniedTriple = Triple.create(deniedSubj, P.asNode(), NodeFactory.createLiteralString("v"));
        final RuleEvaluator eval = new RuleEvaluator(false, (g, t) -> !t.equals(deniedTriple));
        final List<Triple> pattern = triples(Triple.create(s, P.asNode(), NodeFactory.createLiteralString("v")));
        final SecuredFunction f = new SecuredFunction(NodeFactory.createURI(NS + "g"), eval, pattern);

        // Sanity: the original function checks ?s.
        Assert.assertFalse(f.evalSpecial(BindingFactory.binding(s, deniedSubj), null).getBoolean());

        // Rename ?s -> ?x. Pre-fix applyNodeTransform returned `this`, so the
        // captured pattern still referenced ?s and the rename was ignored.
        final NodeTransform rename = n -> n.equals(s) ? x : n;
        final SecuredFunction f2 = (SecuredFunction) f.applyNodeTransform(rename);

        Assert.assertFalse("the renamed variable must now be the one checked",
                f2.evalSpecial(BindingFactory.binding(x, deniedSubj), null).getBoolean());
        Assert.assertTrue("the old variable name is no longer part of the pattern",
                f2.evalSpecial(BindingFactory.binding(s, deniedSubj), null).getBoolean());
    }

    @Test
    public void q7_copySubstituteBakesInValues() {
        final Var s = Var.alloc("s");
        final Node deniedSubj = NodeFactory.createURI(NS + "denied");
        final Triple deniedTriple = Triple.create(deniedSubj, P.asNode(), NodeFactory.createLiteralString("v"));
        final RuleEvaluator eval = new RuleEvaluator(false, (g, t) -> !t.equals(deniedTriple));
        final List<Triple> pattern = triples(Triple.create(s, P.asNode(), NodeFactory.createLiteralString("v")));
        final SecuredFunction f = new SecuredFunction(NodeFactory.createURI(NS + "g"), eval, pattern);

        // Substitute ?s = denied, then evaluate with an EMPTY binding. Pre-fix
        // copySubstitute returned `this`, dropping the substitution -> ?s unbound
        // -> skipped -> wrongly allowed.
        final SecuredFunction f2 = (SecuredFunction) f.copySubstitute(BindingFactory.binding(s, deniedSubj));
        Assert.assertFalse("the substituted value must be baked into the checked triple",
                f2.evalSpecial(BindingFactory.empty(), null).getBoolean());
    }

    private static List<Triple> triples(final Triple... t) {
        final List<Triple> out = new ArrayList<>();
        for (final Triple x : t) {
            out.add(x);
        }
        return out;
    }
}
