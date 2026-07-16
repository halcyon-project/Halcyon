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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.shared.DeleteDeniedException;
import org.junit.Test;

/**
 * Regression tests for {@code SecuredModelImpl.remove(StmtIterator)} (M5): the
 * partial-delete branch (taken when the principal lacks blanket
 * {@code Delete Triple.ANY}) checked each statement but then removed the
 * statements of a fresh, empty model — so it silently deleted nothing. It must
 * now actually delete the authorized statements, and still fail closed if any
 * statement is denied.
 */
public class SecuredRemoveStmtIteratorTest {

    private static final Resource S = ResourceFactory.createResource("http://example.com/s");
    private static final Property P1 = ResourceFactory.createProperty("http://example.com/p1");
    private static final Property P2 = ResourceFactory.createProperty("http://example.com/p2");

    /** Graph-level allow-all; per-triple Delete allowed except a denylist (so blanket Delete-ANY is false). */
    private static SecurityEvaluator partialDelete(final Triple... denyDelete) {
        final Set<Triple> denied = new HashSet<>();
        for (final Triple t : denyDelete) {
            denied.add(t);
        }
        return new SecurityEvaluator() {
            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI) {
                return true;
            }

            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI,
                    final Triple triple) {
                if (action == Action.Delete) {
                    final boolean wildcard = triple.getSubject().equals(Node.ANY)
                            || triple.getPredicate().equals(Node.ANY) || triple.getObject().equals(Node.ANY);
                    // A Delete restriction exists (denied is non-empty), so the
                    // blanket Delete-ANY check fails closed, forcing the
                    // per-statement partial-delete branch under test.
                    return wildcard ? denied.isEmpty() : !denied.contains(triple);
                }
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
        m.add(S, P1, "a");
        m.add(S, P2, "b");
        return m;
    }

    @Test
    public void partialDelete_actuallyRemoves() {
        final Model base = baseModel();
        // A denylist entry on an unrelated triple forces the partial-delete branch
        // (Delete-ANY -> false) without blocking the statements being removed.
        final Triple unrelated = Triple.create(NodeFactory.createURI("http://example.com/other"), P1.asNode(),
                NodeFactory.createLiteralString("x"));
        final Model sm = Factory.getInstance(partialDelete(unrelated), "http://example.com/model", base);

        sm.remove(base.listStatements());

        assertTrue("the authorized statements must actually be removed", base.isEmpty());
    }

    @Test
    public void partialDelete_deniedStatement_removesNothing() {
        final Model base = baseModel();
        final Triple denied = Triple.create(S.asNode(), P2.asNode(), base.createLiteral("b").asNode());
        final Model sm = Factory.getInstance(partialDelete(denied), "http://example.com/model", base);

        try {
            sm.remove(base.listStatements());
            fail("a denied statement must abort the whole remove");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertEquals("nothing may be removed when any statement is denied", 2, base.size());
    }

    @Test
    public void fullDelete_removes() {
        final Model base = baseModel();
        // No denylist: Delete-ANY is true, taking the fast path.
        final Model sm = Factory.getInstance(partialDelete(), "http://example.com/model", base);

        sm.remove(base.listStatements());

        assertTrue(base.isEmpty());
    }
}
