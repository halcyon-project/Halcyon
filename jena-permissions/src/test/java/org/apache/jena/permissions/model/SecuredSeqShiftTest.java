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

import java.util.HashSet;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Seq;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.vocabulary.RDF;
import org.junit.Test;

/**
 * Regression tests for {@code SecuredSeqImpl.add(int,…)} and {@code remove(int)}.
 * <p>
 * Inserting or removing at an index renumbers the trailing members
 * (SeqImpl {@code shiftUp}/{@code shiftDown} delete-and-recreate each), so every
 * shifted triple must be authorized, not just the inserted/removed one. These
 * tests use a per-triple {@link SecurityEvaluator} to prove each shift
 * delete/create is checked and that a denial aborts the whole operation,
 * leaving the Seq intact.
 */
public class SecuredSeqShiftTest {

    /** Grants everything except an explicit per-triple denylist; honors the ANY-wildcard contract. */
    private static final class RuleEvaluator implements SecurityEvaluator {
        private final Set<Triple> denyCreate = new HashSet<>();
        private final Set<Triple> denyDelete = new HashSet<>();

        RuleEvaluator denyCreate(final Triple t) {
            denyCreate.add(t);
            return this;
        }

        RuleEvaluator denyDelete(final Triple t) {
            denyDelete.add(t);
            return this;
        }

        @Override
        public boolean evaluate(final Object principal, final Action action, final Node graphIRI) {
            return true;
        }

        @Override
        public boolean evaluate(final Object principal, final Action action, final Node graphIRI, final Triple triple) {
            final boolean wildcard = triple.getSubject().equals(Node.ANY) || triple.getPredicate().equals(Node.ANY)
                    || triple.getObject().equals(Node.ANY);
            switch (action) {
            case Create:
                return wildcard ? denyCreate.isEmpty() : !denyCreate.contains(triple);
            case Delete:
                return wildcard ? denyDelete.isEmpty() : !denyDelete.contains(triple);
            default:
                return true;
            }
        }

        @Override
        public Object getPrincipal() {
            return "test-principal";
        }

        @Override
        public boolean isPrincipalAuthenticated(final Object principal) {
            return true;
        }
    }

    private static Triple member(final Resource seq, final Property ordinal, final RDFNode value) {
        return Triple.create(seq.asNode(), ordinal.asNode(), value.asNode());
    }

    private static Seq freshSeq() {
        final Model m = ModelFactory.createDefaultModel();
        final Seq s = m.createSeq();
        s.add(m.createLiteral("A"));
        s.add(m.createLiteral("B"));
        s.add(m.createLiteral("C"));
        return s;
    }

    private static Seq secure(final Seq seq, final SecurityEvaluator eval) {
        final Model sm = Factory.getInstance(eval, "http://example.com/model", seq.getModel());
        return sm.getSeq(seq);
    }

    private static void assertIntactABC(final Seq seq) {
        assertEquals(3, seq.size());
        assertEquals("A", seq.getString(1));
        assertEquals("B", seq.getString(2));
        assertEquals("C", seq.getString(3));
    }

    // ---- add(int, …) : shift-up ----

    @Test
    public void add_insert_deniedShiftCreate_isRejected() {
        final Seq seq = freshSeq();
        final RDFNode b = seq.getObject(2);
        // Inserting at _2 shifts B from _2 up to _3: create (seq, _3, B).
        final RuleEvaluator eval = new RuleEvaluator().denyCreate(member(seq, RDF.li(3), b));
        try {
            secure(seq, eval).add(2, seq.getModel().createLiteral("X"));
            fail("insert must authorize the shift-up Create triples");
        } catch (final AddDeniedException expected) {
            // correct
        }
        assertIntactABC(seq);
    }

    @Test
    public void add_insert_deniedShiftDelete_isRejected() {
        final Seq seq = freshSeq();
        final RDFNode b = seq.getObject(2);
        // Inserting at _2 first deletes the old (seq, _2, B) as it shifts up.
        final RuleEvaluator eval = new RuleEvaluator().denyDelete(member(seq, RDF.li(2), b));
        try {
            secure(seq, eval).add(2, seq.getModel().createLiteral("X"));
            fail("insert must authorize deletion of the shifted members");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertIntactABC(seq);
    }

    @Test
    public void add_insert_deniedInsertedTriple_isRejected() {
        final Seq seq = freshSeq();
        final RDFNode x = seq.getModel().createLiteral("X");
        final RuleEvaluator eval = new RuleEvaluator().denyCreate(member(seq, RDF.li(2), x));
        try {
            secure(seq, eval).add(2, x);
            fail("insert must authorize the inserted triple itself");
        } catch (final AddDeniedException expected) {
            // correct
        }
        assertIntactABC(seq);
    }

    @Test
    public void add_insert_fullyPermitted_succeeds() {
        final Seq seq = freshSeq();
        secure(seq, new RuleEvaluator()).add(2, seq.getModel().createLiteral("X"));
        assertEquals(4, seq.size());
        assertEquals("A", seq.getString(1));
        assertEquals("X", seq.getString(2));
        assertEquals("B", seq.getString(3));
        assertEquals("C", seq.getString(4));
    }

    @Test
    public void add_append_needsOnlyCreate() {
        final Seq seq = freshSeq();
        final RDFNode a = seq.getObject(1);
        final RDFNode b = seq.getObject(2);
        final RDFNode c = seq.getObject(3);
        // Appending at _4 shifts nothing; denying Delete on existing members must not block it.
        final RuleEvaluator eval = new RuleEvaluator().denyDelete(member(seq, RDF.li(1), a))
                .denyDelete(member(seq, RDF.li(2), b)).denyDelete(member(seq, RDF.li(3), c));
        secure(seq, eval).add(4, seq.getModel().createLiteral("D"));
        assertEquals(4, seq.size());
        assertEquals("D", seq.getString(4));
    }

    // ---- remove(int) : shift-down ----

    @Test
    public void remove_deniedShiftCreate_isRejected() {
        final Seq seq = freshSeq();
        final RDFNode b = seq.getObject(2);
        // Removing _1 shifts B from _2 down to _1: create (seq, _1, B).
        final RuleEvaluator eval = new RuleEvaluator().denyCreate(member(seq, RDF.li(1), b));
        try {
            secure(seq, eval).remove(1);
            fail("remove(int) must authorize the shift-down Create triples");
        } catch (final AddDeniedException expected) {
            // correct
        }
        assertIntactABC(seq);
    }

    @Test
    public void remove_deniedShiftDelete_isRejected() {
        final Seq seq = freshSeq();
        final RDFNode c = seq.getObject(3);
        // Removing _1 deletes (seq, _3, C) as C shifts down.
        final RuleEvaluator eval = new RuleEvaluator().denyDelete(member(seq, RDF.li(3), c));
        try {
            secure(seq, eval).remove(1);
            fail("remove(int) must authorize deletion of the shifted members");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertIntactABC(seq);
    }

    @Test
    public void remove_fullyPermitted_succeeds() {
        final Seq seq = freshSeq();
        secure(seq, new RuleEvaluator()).remove(1);
        assertEquals(2, seq.size());
        assertEquals("B", seq.getString(1));
        assertEquals("C", seq.getString(2));
    }

    @Test
    public void remove_lastMember_needsOnlyDelete() {
        final Seq seq = freshSeq();
        final RDFNode b = seq.getObject(2);
        // Removing the last member shifts nothing; denying Create must not block it.
        final RuleEvaluator eval = new RuleEvaluator().denyCreate(member(seq, RDF.li(1), b));
        secure(seq, eval).remove(3);
        assertEquals(2, seq.size());
        assertEquals("A", seq.getString(1));
        assertEquals("B", seq.getString(2));
    }
}
