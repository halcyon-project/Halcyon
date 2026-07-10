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
package org.apache.jena.permissions.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.rdf.model.Bag;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.Test;

/**
 * Regression tests for M4: {@code Model.isEmpty()}, {@code Container.size()} and
 * {@code Graph.isEmpty()} reported the unfiltered base contents, ignoring
 * triple-level Read — so a principal granted graph Read but denied every triple
 * saw {@code size()==0}/an empty iterator yet {@code isEmpty()==false} (and the
 * container's true count). They must now agree with the read-filtered view.
 */
public class SecuredEmptySizeTest {

    private static final Property P = ResourceFactory.createProperty("http://example.com/p");

    /** Graph-level allow-all; triple-level: deny only Read (so handles are still obtainable). */
    private static SecurityEvaluator denyTripleRead() {
        return new SecurityEvaluator() {
            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI) {
                return true;
            }

            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI,
                    final Triple triple) {
                return action != Action.Read;
            }

            @Override
            public Object getPrincipal() {
                return "test-principal";
            }

            @Override
            public boolean isPrincipalAuthenticated(final Object principal) {
                return true;
            }
        };
    }

    private static SecurityEvaluator allowAll() {
        return new SecurityEvaluator() {
            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI) {
                return true;
            }

            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI,
                    final Triple triple) {
                return true;
            }

            @Override
            public Object getPrincipal() {
                return "test-principal";
            }

            @Override
            public boolean isPrincipalAuthenticated(final Object principal) {
                return true;
            }
        };
    }

    private static Model baseModel() {
        final Model m = ModelFactory.createDefaultModel();
        m.add(m.createResource("http://example.com/s"), P, "o");
        return m;
    }

    // ---- Model.isEmpty() consistent with size() ----

    @Test
    public void modelIsEmpty_hidesUnreadableStatements() {
        final Model sm = Factory.getInstance(denyTripleRead(), "http://example.com/model", baseModel());
        assertTrue("a model whose statements are all unreadable must appear empty", sm.isEmpty());
        assertEquals("isEmpty() must agree with size()", 0L, sm.size());
    }

    @Test
    public void modelIsEmpty_permitted() {
        final Model sm = Factory.getInstance(allowAll(), "http://example.com/model", baseModel());
        assertFalse(sm.isEmpty());
        assertEquals(1L, sm.size());
    }

    // ---- Container.size() counts only readable members ----

    @Test
    public void containerSize_countsOnlyReadable() {
        final Model base = ModelFactory.createDefaultModel();
        final Bag bag = base.createBag();
        bag.add("a");
        bag.add("b");
        final Bag secured = Factory.getInstance(denyTripleRead(), "http://example.com/model", base).getBag(bag);
        assertEquals("unreadable members must not be counted", 0, secured.size());
    }

    @Test
    public void containerSize_permitted() {
        final Model base = ModelFactory.createDefaultModel();
        final Bag bag = base.createBag();
        bag.add("a");
        bag.add("b");
        final Bag secured = Factory.getInstance(allowAll(), "http://example.com/model", base).getBag(bag);
        assertEquals(2, secured.size());
    }

    // ---- Graph.isEmpty() (same defect) ----

    private static Graph baseGraph() {
        final Graph g = GraphFactory.createDefaultGraph();
        g.add(Triple.create(NodeFactory.createURI("http://example.com/s"), NodeFactory.createURI("http://example.com/p"),
                NodeFactory.createURI("http://example.com/o")));
        return g;
    }

    @Test
    public void graphIsEmpty_hidesUnreadableTriples() {
        final Graph sg = Factory.getInstance(denyTripleRead(), "http://example.com/graph", baseGraph());
        assertTrue("a graph whose triples are all unreadable must appear empty", sg.isEmpty());
        assertEquals("isEmpty() must agree with size()", 0, sg.size());
    }

    @Test
    public void graphIsEmpty_permitted() {
        final Graph sg = Factory.getInstance(allowAll(), "http://example.com/graph", baseGraph());
        assertFalse(sg.isEmpty());
        assertEquals(1, sg.size());
    }
}
