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

import java.util.List;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.permissions.model.impl.SecuredRDFListImpl;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.vocabulary.RDF;
import org.junit.Test;

/**
 * Regression tests for {@code SecuredRDFListImpl.remove}/{@code removeHead} (M6):
 * removing a list element rewrites the predecessor's {@code rdf:rest} pointer
 * (a delete + create) and deletes the cell's {@code rdf:rest}, but the wrapper
 * only authorized the cell's {@code rdf:first}. These tests deny {@code rdf:rest}
 * mutations specifically and confirm the removal is now rejected.
 */
public class SecuredRDFListRemoveTest {

    private static final Resource A = ResourceFactory.createResource("http://example.com/a");
    private static final Resource B = ResourceFactory.createResource("http://example.com/b");
    private static final Resource C = ResourceFactory.createResource("http://example.com/c");

    /** Graph-level allow-all; denies the given action for triples whose predicate is {@code rdf:rest}. */
    private static SecurityEvaluator denyRest(final SecurityEvaluator.Action denied) {
        return new SecurityEvaluator() {
            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI) {
                return true;
            }

            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI,
                    final Triple triple) {
                if (action == denied) {
                    final boolean wildcard = triple.getSubject().equals(Node.ANY)
                            || triple.getPredicate().equals(Node.ANY) || triple.getObject().equals(Node.ANY);
                    // A restriction of this action exists, so a wildcard check must
                    // fail closed (forcing the per-triple checking path).
                    return wildcard ? false : !RDF.rest.asNode().equals(triple.getPredicate());
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

    private static RDFList freshList(final Model m) {
        return m.createList(new RDFNode[] { A, B, C });
    }

    private static SecuredRDFList secure(final Model base, final RDFList list, final SecurityEvaluator eval) {
        return SecuredRDFListImpl.getInstance(Factory.getInstance(eval, "http://example.com/model", base), list);
    }

    @Test
    public void removeNonHead_deniedRestCreate_isRejected() {
        final Model base = ModelFactory.createDefaultModel();
        final RDFList list = freshList(base);
        try {
            secure(base, list, denyRest(SecurityEvaluator.Action.Create)).remove(B);
            fail("removing a middle element must authorize the rdf:rest relink create");
        } catch (final AddDeniedException expected) {
            // correct
        }
        assertEquals("the list must be unchanged", 3, list.size());
        assertTrue(list.contains(B));
    }

    @Test
    public void removeNonHead_deniedRestDelete_isRejected() {
        final Model base = ModelFactory.createDefaultModel();
        final RDFList list = freshList(base);
        try {
            secure(base, list, denyRest(SecurityEvaluator.Action.Delete)).remove(B);
            fail("removing a middle element must authorize the predecessor rdf:rest delete");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertEquals("the list must be unchanged", 3, list.size());
        assertTrue(list.contains(B));
    }

    @Test
    public void removeNonHead_permitted() {
        final Model base = ModelFactory.createDefaultModel();
        final RDFList list = freshList(base);
        secure(base, list, allowAll()).remove(B);
        final List<RDFNode> l = list.asJavaList();
        assertEquals(2, l.size());
        assertEquals(A, l.get(0));
        assertEquals(C, l.get(1));
    }

    @Test
    public void removeHead_deniedRestDelete_isRejected() {
        final Model base = ModelFactory.createDefaultModel();
        final RDFList list = freshList(base);
        try {
            secure(base, list, denyRest(SecurityEvaluator.Action.Delete)).removeHead();
            fail("removeHead must authorize deletion of the head cell's rdf:rest");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertEquals("the list must be unchanged", 3, list.size());
    }

    @Test
    public void removeHead_permitted() {
        final Model base = ModelFactory.createDefaultModel();
        final RDFList list = freshList(base);
        final RDFList result = secure(base, list, allowAll()).removeHead();
        final List<RDFNode> l = result.asJavaList();
        assertEquals(2, l.size());
        assertEquals(B, l.get(0));
        assertEquals(C, l.get(1));
    }
}
