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
package org.apache.jena.permissions.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphUtil;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.impl.GraphWithPerform;
import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.MockSecurityEvaluator;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.UpdateDeniedException;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.Test;

/**
 * Regression tests for the {@link GraphWithPerform} write path.
 * <p>
 * A {@link SecuredGraph} proxy inherits every interface of the graph it wraps,
 * so it advertises {@code GraphWithPerform}. Jena bulk operations
 * (notably {@link GraphUtil#addInto}/{@link GraphUtil#deleteFrom}, and thus
 * {@code Model.add(Model)} / {@code RDFDataMgr.read} into a wrapped graph)
 * call {@code performAdd}/{@code performDelete} <em>directly</em> rather than
 * {@code add}/{@code delete}. If the secured graph does not override the
 * {@code perform*} methods, those calls fall through the proxy to the
 * unsecured base graph. These tests assert the {@code perform*} methods are
 * authorized with the same checks as {@code add}/{@code delete}.
 */
public class SecuredGraphPerformTest {

    private static final Node S = NodeFactory.createURI("http://example.com/s");
    private static final Node P = NodeFactory.createURI("http://example.com/p");
    private static final Node O = NodeFactory.createURI("http://example.com/o");
    private static final Triple T = Triple.create(S, P, O);

    // loggedIn, create, read, update, delete, forceTripleChecks, hardReadError
    private static MockSecurityEvaluator eval(final boolean create, final boolean update, final boolean delete) {
        return new MockSecurityEvaluator(true, create, true, update, delete, false, true);
    }

    private Graph base() {
        return GraphFactory.createDefaultGraph();
    }

    private GraphWithPerform secured(final Graph base, final MockSecurityEvaluator ev) {
        // The proxy implements GraphWithPerform because SecuredGraphImpl does
        // and because the base graph does; this cast is exactly what
        // GraphUtil performs internally.
        return (GraphWithPerform) Factory.getInstance(ev, "http://example.com/graph", base);
    }

    @Test
    public void performAdd_deniedWhenUpdateDenied() {
        final Graph base = base();
        final GraphWithPerform g = secured(base, eval(true, false, true));
        try {
            g.performAdd(T);
            fail("performAdd must not bypass the Update check");
        } catch (final UpdateDeniedException expected) {
            // correct
        }
        assertTrue("nothing should have been written to the base graph", base.isEmpty());
    }

    @Test
    public void performAdd_deniedWhenCreateDenied() {
        final Graph base = base();
        // Update allowed, Create denied: proves the triple-level Create check fires.
        final GraphWithPerform g = secured(base, eval(false, true, true));
        try {
            g.performAdd(T);
            fail("performAdd must not bypass the Create check");
        } catch (final AddDeniedException expected) {
            // correct
        }
        assertTrue("nothing should have been written to the base graph", base.isEmpty());
    }

    @Test
    public void performAdd_allowed() {
        final Graph base = base();
        final GraphWithPerform g = secured(base, eval(true, true, true));
        g.performAdd(T);
        assertEquals(1, base.size());
        assertTrue(base.contains(T));
    }

    @Test
    public void performDelete_deniedWhenUpdateDenied() {
        final Graph base = base();
        base.add(T);
        final GraphWithPerform g = secured(base, eval(true, false, true));
        try {
            g.performDelete(T);
            fail("performDelete must not bypass the Update check");
        } catch (final UpdateDeniedException expected) {
            // correct
        }
        assertTrue("the triple must survive a denied delete", base.contains(T));
    }

    @Test
    public void performDelete_deniedWhenDeleteDenied() {
        final Graph base = base();
        base.add(T);
        // Update allowed, Delete denied: proves the triple-level Delete check fires.
        final GraphWithPerform g = secured(base, eval(true, true, false));
        try {
            g.performDelete(T);
            fail("performDelete must not bypass the Delete check");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertTrue("the triple must survive a denied delete", base.contains(T));
    }

    @Test
    public void performDelete_allowed() {
        final Graph base = base();
        base.add(T);
        final GraphWithPerform g = secured(base, eval(true, true, true));
        g.performDelete(T);
        assertTrue(base.isEmpty());
    }

    /**
     * The real-world attack path: a bulk copy via {@link GraphUtil#addInto},
     * which casts the destination to {@code GraphWithPerform} and calls
     * {@code performAdd} per triple. This is what {@code Model.add(Model)} uses.
     */
    @Test
    public void graphUtilAddInto_isChecked() {
        final Graph base = base();
        final Graph src = base();
        src.add(T);

        final Graph denied = (Graph) secured(base, eval(false, true, true));
        try {
            GraphUtil.addInto(denied, src);
            fail("GraphUtil.addInto must not bypass the secured graph's Create check");
        } catch (final AddDeniedException expected) {
            // correct
        }
        assertTrue("bulk add through performAdd must not reach the base graph", base.isEmpty());

        final Graph allowed = (Graph) secured(base, eval(true, true, true));
        GraphUtil.addInto(allowed, src);
        assertTrue("bulk add must succeed when permitted", base.contains(T));
    }

    /**
     * The delete counterpart: {@link GraphUtil#deleteFrom} casts to
     * {@code GraphWithPerform} and calls {@code performDelete} per triple.
     */
    @Test
    public void graphUtilDeleteFrom_isChecked() {
        final Graph base = base();
        base.add(T);
        final Graph toRemove = base();
        toRemove.add(T);

        final Graph denied = (Graph) secured(base, eval(true, true, false));
        try {
            GraphUtil.deleteFrom(denied, toRemove);
            fail("GraphUtil.deleteFrom must not bypass the secured graph's Delete check");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertTrue("bulk delete through performDelete must not reach the base graph", base.contains(T));

        final Graph allowed = (Graph) secured(base, eval(true, true, true));
        GraphUtil.deleteFrom(allowed, toRemove);
        assertFalse("bulk delete must succeed when permitted", base.contains(T));
    }
}
