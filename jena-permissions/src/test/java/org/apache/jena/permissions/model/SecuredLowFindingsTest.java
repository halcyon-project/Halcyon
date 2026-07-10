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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.rdf.model.Alt;
import org.apache.jena.rdf.model.AltHasNoDefaultException;
import org.apache.jena.rdf.model.Bag;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.vocabulary.RDF;
import org.junit.Test;

/**
 * Regression tests for the behaviorally-observable low-severity findings
 * L2 (containsAny), L3 (container add off-by-one), L4 (listObjectsOfProperty
 * ignores subject), L5 (Alt default is rdf:_1) and L9 (inModel re-wraps).
 */
public class SecuredLowFindingsTest {

    private static final Property P = ResourceFactory.createProperty("http://example.com/p");

    /** Allows everything except an explicit per-triple Read/Create denylist. */
    private static final class RuleEvaluator implements SecurityEvaluator {
        private final Set<Triple> denyRead = new HashSet<>();
        private final Set<Triple> denyCreate = new HashSet<>();

        RuleEvaluator denyRead(final Triple t) {
            denyRead.add(t);
            return this;
        }

        RuleEvaluator denyCreate(final Triple t) {
            denyCreate.add(t);
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
            case Read:
                return wildcard ? denyRead.isEmpty() : !denyRead.contains(triple);
            case Create:
                return wildcard ? denyCreate.isEmpty() : !denyCreate.contains(triple);
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

    private static Model secure(final Model base, final SecurityEvaluator eval) {
        return Factory.getInstance(eval, "http://example.com/model", base);
    }

    @Test
    public void l2_containsAny_reportsReadableOverlap() {
        final Model base = ModelFactory.createDefaultModel();
        final Resource s = base.createResource("http://example.com/s");
        base.add(s, P, "o");
        final Model other = ModelFactory.createDefaultModel();
        other.add(s, P, "o"); // overlaps the base
        other.add(other.createResource("http://example.com/x"), P, "z"); // not in the base

        // Overlap exists and is readable -> containsAny is true (old code returned
        // false: the filtered result was discarded, and containsAll was used).
        assertTrue(secure(base, new RuleEvaluator()).containsAny(other));

        // Deny reading the only overlapping statement -> no readable overlap.
        final RuleEvaluator eval = new RuleEvaluator()
                .denyRead(Triple.create(s.asNode(), P.asNode(), base.createLiteral("o").asNode()));
        assertFalse(secure(base, eval).containsAny(other));
    }

    @Test
    public void l3_containerAdd_checksCorrectOrdinal() {
        final Model base = ModelFactory.createDefaultModel();
        final Bag bag = base.createBag();
        final RDFNode v = base.createLiteral("x");
        // Base appends the first element at rdf:_1; deny Create of exactly that.
        final Triple slot1 = Triple.create(bag.asNode(), RDF.li(1).asNode(), v.asNode());
        final Bag secured = secure(base, new RuleEvaluator().denyCreate(slot1)).getBag(bag);
        try {
            secured.add(v);
            fail("add() must authorize rdf:_(size()+1), the slot the base actually uses");
        } catch (final AddDeniedException expected) {
            // correct
        }
        assertTrue("nothing should have been added", bag.size() == 0);
    }

    @Test
    public void l4_listObjectsOfProperty_respectsSubject() {
        final Model base = ModelFactory.createDefaultModel();
        final Resource s1 = base.createResource("http://example.com/s1");
        final Resource s3 = base.createResource("http://example.com/s3");
        final Resource o = base.createResource("http://example.com/o");
        base.add(s1, P, o); // unreadable below
        base.add(s3, P, o); // readable, shares the object o

        // (s1, p, o) is unreadable; (s3, p, o) is readable and shares o.
        final RuleEvaluator eval = new RuleEvaluator().denyRead(Triple.create(s1.asNode(), P.asNode(), o.asNode()));
        final Set<RDFNode> objects = secure(base, eval).listObjectsOfProperty(s1, P).toSet();
        assertFalse("o must not be an object of s1 when (s1,p,o) is unreadable", objects.contains(o));
    }

    @Test
    public void l5_altGetDefault_targetsRdf1() {
        final Model base = ModelFactory.createDefaultModel();
        final Alt alt = base.createAlt();
        alt.add("A"); // rdf:_1
        alt.add("B"); // rdf:_2
        final RDFNode a = alt.getProperty(RDF.li(1)).getObject();
        // The default is rdf:_1; deny reading it (but allow rdf:_2).
        final RuleEvaluator eval = new RuleEvaluator()
                .denyRead(Triple.create(alt.asNode(), RDF.li(1).asNode(), a.asNode()));
        final Alt secured = secure(base, eval).getAlt(alt);
        try {
            secured.getDefault();
            fail("getDefault() must target rdf:_1 (unreadable here), not the first readable member");
        } catch (final AltHasNoDefaultException expected) {
            // correct
        }
    }

    @Test
    public void l9_inModel_returnsSecuredResource() {
        final Model base = ModelFactory.createDefaultModel();
        base.createResource("http://example.com/r");
        final Model sm = secure(base, new RuleEvaluator());
        final Model other = secure(ModelFactory.createDefaultModel(), new RuleEvaluator());
        final Resource inOther = ((SecuredResource) sm.getResource("http://example.com/r")).inModel(other);
        assertTrue("inModel(SecuredModel) must return a secured resource", inOther instanceof SecuredResource);
    }
}
