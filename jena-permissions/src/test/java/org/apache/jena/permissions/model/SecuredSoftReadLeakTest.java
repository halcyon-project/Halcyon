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
import static org.junit.Assert.fail;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Seq;
import org.apache.jena.rdf.model.SeqIndexBoundsException;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.ReadDeniedException;
import org.junit.Test;

/**
 * Regression tests for the soft-read leak family (M3): several accessors
 * computed a triple-level Read check but ignored the result, returning the
 * value of a triple the principal may not read (in the default
 * {@code isHardReadError()==false} mode). These tests grant graph-level Read but
 * deny triple-level Read of a subject's statements, then confirm the value is no
 * longer leaked.
 */
public class SecuredSoftReadLeakTest {

    private static final Property P = ResourceFactory.createProperty("http://example.com/p");

    /** Allows everything, except (when {@code deniedSubject} is non-null) triple-level Read of that subject. */
    private static SecurityEvaluator evaluator(final Node deniedSubject) {
        return new SecurityEvaluator() {
            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI) {
                return true; // graph-level: everything (incl. Read) allowed
            }

            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI,
                    final Triple triple) {
                return !(action == Action.Read && triple.getSubject().equals(deniedSubject));
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

    // ---- SecuredStatementImpl accessors: throw on an unreadable triple ----

    /** Obtains a statement handle for an unreadable triple via the create branch of asStatement(). */
    private static Statement unreadableStatement(final Model base, final Node s, final Triple t) {
        return secure(base, evaluator(s)).asStatement(t);
    }

    @Test
    public void statementGetObject_throwsForUnreadableTriple() {
        final Model base = ModelFactory.createDefaultModel();
        final org.apache.jena.rdf.model.Resource s = base.createResource("http://example.com/s");
        base.add(s, P, "secret");
        final Triple t = Triple.create(s.asNode(), P.asNode(), base.createLiteral("secret").asNode());
        try {
            unreadableStatement(base, s.asNode(), t).getObject();
            fail("getObject() must not return the value of an unreadable triple");
        } catch (final ReadDeniedException expected) {
            // correct
        }
    }

    @Test
    public void statementAsTriple_throwsForUnreadableTriple() {
        final Model base = ModelFactory.createDefaultModel();
        final org.apache.jena.rdf.model.Resource s = base.createResource("http://example.com/s");
        base.add(s, P, "secret");
        final Triple t = Triple.create(s.asNode(), P.asNode(), base.createLiteral("secret").asNode());
        try {
            unreadableStatement(base, s.asNode(), t).asTriple();
            fail("asTriple() must not return the raw triple when it is unreadable");
        } catch (final ReadDeniedException expected) {
            // correct
        }
    }

    @Test
    public void statementGetString_throwsForUnreadableTriple() {
        final Model base = ModelFactory.createDefaultModel();
        final org.apache.jena.rdf.model.Resource s = base.createResource("http://example.com/s");
        base.add(s, P, "secret");
        final Triple t = Triple.create(s.asNode(), P.asNode(), base.createLiteral("secret").asNode());
        try {
            unreadableStatement(base, s.asNode(), t).getString();
            fail("getString() must not return the value of an unreadable triple");
        } catch (final ReadDeniedException expected) {
            // correct
        }
    }

    @Test
    public void statementGetObject_permitted_returnsValue() {
        final Model base = ModelFactory.createDefaultModel();
        final org.apache.jena.rdf.model.Resource s = base.createResource("http://example.com/s");
        base.add(s, P, "secret");
        final Triple t = Triple.create(s.asNode(), P.asNode(), base.createLiteral("secret").asNode());
        final Statement stmt = secure(base, evaluator(null)).asStatement(t);
        assertEquals("secret", stmt.getObject().asLiteral().getString());
    }

    // ---- SecuredSeqImpl by-index getters: hide an unreadable member ----

    private static Seq freshSeq(final Model m) {
        final Seq s = m.createSeq();
        s.add("secret");
        return s;
    }

    @Test
    public void seqGetString_hidesUnreadableMember() {
        final Model base = ModelFactory.createDefaultModel();
        final Seq seq = freshSeq(base);
        final Seq secured = secure(base, evaluator(seq.asNode())).getSeq(seq);
        try {
            secured.getString(1);
            fail("an unreadable Seq member must not be returned");
        } catch (final SeqIndexBoundsException expected) {
            // correct — hidden as if absent
        }
    }

    @Test
    public void seqGetObject_hidesUnreadableMember() {
        final Model base = ModelFactory.createDefaultModel();
        final Seq seq = freshSeq(base);
        final Seq secured = secure(base, evaluator(seq.asNode())).getSeq(seq);
        try {
            secured.getObject(1);
            fail("an unreadable Seq member must not be returned");
        } catch (final SeqIndexBoundsException expected) {
            // correct
        }
    }

    @Test
    public void seqGetString_permitted_returnsValue() {
        final Model base = ModelFactory.createDefaultModel();
        final Seq seq = freshSeq(base);
        final Seq secured = secure(base, evaluator(null)).getSeq(seq);
        assertEquals("secret", secured.getString(1));
    }
}
