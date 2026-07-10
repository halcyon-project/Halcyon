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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.rdf.model.Alt;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Seq;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.vocabulary.RDF;
import org.junit.Test;

/**
 * Regression test for the {@code createSeq()} wrong-allow (M1): the secured
 * wrapper used to authorize {@code (…, rdf:type, rdf:Alt)} while the base
 * {@code createSeq} inserts {@code (…, rdf:type, rdf:Seq)}. A policy that keys
 * on the RDF container type could therefore create a Seq without Seq
 * permission. These tests use an evaluator that grants Create of
 * {@code rdf:Alt}-typed triples but denies {@code rdf:Seq}-typed ones.
 */
public class SecuredCreateSeqTest {

    /** Allows everything except Create of a {@code (…, …, rdf:Seq)} triple. */
    private static SecurityEvaluator seqCreateDenied() {
        return new SecurityEvaluator() {
            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI) {
                return true;
            }

            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI,
                    final Triple triple) {
                if (action == Action.Create) {
                    final boolean wildcard = triple.getSubject().equals(Node.ANY)
                            || triple.getPredicate().equals(Node.ANY) || triple.getObject().equals(Node.ANY);
                    // A Create restriction exists, so a wildcard check must fail closed.
                    return wildcard ? false : !RDF.Seq.asNode().equals(triple.getObject());
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

    private static Model secure(final Model base, final SecurityEvaluator eval) {
        return Factory.getInstance(eval, "http://example.com/model", base);
    }

    @Test
    public void createSeq_deniedWhenSeqCreateDenied() {
        final Model base = ModelFactory.createDefaultModel();
        try {
            secure(base, seqCreateDenied()).createSeq();
            fail("createSeq() must authorize (…, rdf:type, rdf:Seq), not rdf:Alt");
        } catch (final AddDeniedException expected) {
            // correct
        }
        assertTrue("nothing should have been created", base.isEmpty());
    }

    @Test
    public void createSeqUri_deniedWhenSeqCreateDenied() {
        final Model base = ModelFactory.createDefaultModel();
        try {
            secure(base, seqCreateDenied()).createSeq("http://example.com/theSeq");
            fail("createSeq(uri) must authorize (uri, rdf:type, rdf:Seq), not rdf:Alt");
        } catch (final AddDeniedException expected) {
            // correct
        }
        assertTrue("nothing should have been created", base.isEmpty());
    }

    @Test
    public void createAlt_stillAllowed_whenOnlySeqDenied() {
        // The fix is Seq-specific: denying only rdf:Seq creation must not affect createAlt.
        final Model base = ModelFactory.createDefaultModel();
        final Alt alt = secure(base, seqCreateDenied()).createAlt();
        assertNotNull(alt);
        assertTrue(base.contains(null, RDF.type, RDF.Alt));
    }

    @Test
    public void createSeq_permitted_succeeds() {
        final Model base = ModelFactory.createDefaultModel();
        final Seq seq = secure(base, allowAll()).createSeq();
        assertNotNull(seq);
        assertTrue("a Seq should have been created in the base model", base.contains(null, RDF.type, RDF.Seq));
    }
}
