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
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.rdf.model.Bag;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Seq;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.vocabulary.RDF;
import org.junit.Test;

/**
 * Regression tests for {@code SecuredContainerImpl.remove(Statement)}.
 * <p>
 * Removing a non-last member of a container is not a simple delete: to stay
 * gap-free, Bag/Alt move the last member into the vacated slot
 * ({@code delete last}, {@code create in slot}) and Seq shifts every following
 * member down. Those secondary triples must be authorized too. These tests use
 * a per-triple {@link SecurityEvaluator} to prove each secondary delete/create
 * is checked, that a denied secondary triple aborts the whole operation
 * leaving the container intact, and that a fully-permitted removal still works.
 */
public class SecuredContainerRemoveTest {

    /**
     * Permits every action at graph and triple level except an explicit
     * per-triple denylist. Honors the {@code SecurityEvaluator} wildcard
     * contract: a check on a triple containing {@code ANY} returns
     * {@code false} when any restriction of that action exists, forcing the
     * secured wrapper down its explicit per-triple checking path.
     */
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

    private static Triple member(final Resource container, final Property ordinal, final RDFNode value) {
        return Triple.create(container.asNode(), ordinal.asNode(), value.asNode());
    }

    private static Bag freshBag() {
        final Model m = ModelFactory.createDefaultModel();
        final Bag b = m.createBag();
        b.add(m.createLiteral("A"));
        b.add(m.createLiteral("B"));
        b.add(m.createLiteral("C"));
        return b;
    }

    private static Seq freshSeq() {
        final Model m = ModelFactory.createDefaultModel();
        final Seq s = m.createSeq();
        s.add(m.createLiteral("A"));
        s.add(m.createLiteral("B"));
        s.add(m.createLiteral("C"));
        return s;
    }

    private static Bag secure(final Bag bag, final SecurityEvaluator eval) {
        final Model sm = Factory.getInstance(eval, "http://example.com/model", bag.getModel());
        return sm.getBag(bag);
    }

    private static Seq secure(final Seq seq, final SecurityEvaluator eval) {
        final Model sm = Factory.getInstance(eval, "http://example.com/model", seq.getModel());
        return sm.getSeq(seq);
    }

    // ---- Bag / Alt: swap-with-last ----

    @Test
    public void bag_removeNonLast_deniedSecondaryCreate_isRejected() {
        final Bag bag = freshBag();
        final RDFNode c = bag.getProperty(RDF.li(3)).getObject();
        final Statement first = bag.getProperty(RDF.li(1));
        // Removing member _1 moves the last member (C) into slot _1: create (bag, _1, C).
        final RuleEvaluator eval = new RuleEvaluator().denyCreate(member(bag, RDF.li(1), c));

        try {
            secure(bag, eval).remove(first);
            fail("remove(Statement) must authorize the swap-in Create triple");
        } catch (final AddDeniedException expected) {
            // correct
        }
        assertEquals("container must be untouched by a denied removal", 3, bag.size());
        assertTrue(bag.contains(bag.getModel().createLiteral("A")));
    }

    @Test
    public void bag_removeNonLast_deniedSecondaryDelete_isRejected() {
        final Bag bag = freshBag();
        final RDFNode c = bag.getProperty(RDF.li(3)).getObject();
        final Statement first = bag.getProperty(RDF.li(1));
        // Removing member _1 also deletes the last triple (bag, _3, C).
        final RuleEvaluator eval = new RuleEvaluator().denyDelete(member(bag, RDF.li(3), c));

        try {
            secure(bag, eval).remove(first);
            fail("remove(Statement) must authorize deletion of the moved last member");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertEquals("container must be untouched by a denied removal", 3, bag.size());
    }

    @Test
    public void bag_removeNonLast_fullyPermitted_succeeds() {
        final Bag bag = freshBag();
        final Statement first = bag.getProperty(RDF.li(1));

        secure(bag, new RuleEvaluator()).remove(first);

        assertEquals(2, bag.size());
        assertFalse("A was removed", bag.contains(bag.getModel().createLiteral("A")));
        assertTrue(bag.contains(bag.getModel().createLiteral("B")));
        assertTrue("C moved into the vacated slot", bag.contains(bag.getModel().createLiteral("C")));
    }

    @Test
    public void bag_removeLast_needsOnlyDelete() {
        final Bag bag = freshBag();
        final RDFNode a = bag.getProperty(RDF.li(1)).getObject();
        final RDFNode b = bag.getProperty(RDF.li(2)).getObject();
        final Statement last = bag.getProperty(RDF.li(3));
        // Removing the LAST member is a plain delete: denying Create on the
        // other slots must NOT block it (no over-checking).
        final RuleEvaluator eval = new RuleEvaluator().denyCreate(member(bag, RDF.li(1), a))
                .denyCreate(member(bag, RDF.li(2), b));

        secure(bag, eval).remove(last);

        assertEquals(2, bag.size());
        assertFalse(bag.contains(bag.getModel().createLiteral("C")));
    }

    // ---- Seq: shift-down ----

    @Test
    public void seq_removeNonLast_deniedShiftCreate_isRejected() {
        final Seq seq = freshSeq();
        final RDFNode b = seq.getProperty(RDF.li(2)).getObject();
        final Statement first = seq.getProperty(RDF.li(1));
        // Removing _1 shifts _2 (B) down to _1: create (seq, _1, B).
        final RuleEvaluator eval = new RuleEvaluator().denyCreate(member(seq, RDF.li(1), b));

        try {
            secure(seq, eval).remove(first);
            fail("remove(Statement) on a Seq must authorize the shift-down Create triples");
        } catch (final AddDeniedException expected) {
            // correct
        }
        assertEquals(3, seq.size());
        assertEquals("order preserved after a denied removal", "A", seq.getString(1));
    }

    @Test
    public void seq_removeNonLast_deniedShiftDelete_isRejected() {
        final Seq seq = freshSeq();
        final RDFNode bVal = seq.getProperty(RDF.li(2)).getObject();
        final Statement first = seq.getProperty(RDF.li(1));
        // Removing _1 deletes the following member triple (seq, _2, B) as it shifts.
        final RuleEvaluator eval = new RuleEvaluator().denyDelete(member(seq, RDF.li(2), bVal));

        try {
            secure(seq, eval).remove(first);
            fail("remove(Statement) on a Seq must authorize deletion of the shifted triples");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertEquals(3, seq.size());
    }

    @Test
    public void seq_removeNonLast_fullyPermitted_succeeds() {
        final Seq seq = freshSeq();
        final Statement first = seq.getProperty(RDF.li(1));

        secure(seq, new RuleEvaluator()).remove(first);

        assertEquals(2, seq.size());
        assertEquals("B shifted down to _1", "B", seq.getString(1));
        assertEquals("C shifted down to _2", "C", seq.getString(2));
    }
}
