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
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.UpdateDeniedException;
import org.junit.Test;

/**
 * Regression tests for the literal-typing check mismatch (M2): the secured
 * wrapper authorized a different object node than the base actually writes.
 * <ul>
 * <li>{@code changeLiteralObject(primitive)} checked an {@code xsd:string}
 * while the base writes {@code createTypedLiteral(o)}.</li>
 * <li>{@code createStatement(s, p, String)} checked a URI node while the base
 * stores a string literal.</li>
 * </ul>
 * These tests use a per-triple {@link SecurityEvaluator} to confirm the check
 * now targets the node the base writes.
 */
public class SecuredLiteralCheckTest {

    private static final Resource S = ResourceFactory.createResource("http://example.com/s");
    private static final Property P = ResourceFactory.createProperty("http://example.com/p");

    /** Grants everything except an explicit per-triple Create denylist. */
    private static final class RuleEvaluator implements SecurityEvaluator {
        private final Set<Triple> denyCreate = new HashSet<>();

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
            if (action == Action.Create) {
                final boolean wildcard = triple.getSubject().equals(Node.ANY) || triple.getPredicate().equals(Node.ANY)
                        || triple.getObject().equals(Node.ANY);
                return wildcard ? denyCreate.isEmpty() : !denyCreate.contains(triple);
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
    }

    private static Triple triple(final RDFNode o) {
        return Triple.create(S.asNode(), P.asNode(), o.asNode());
    }

    private static Model secure(final Model base, final SecurityEvaluator eval) {
        return Factory.getInstance(eval, "http://example.com/model", base);
    }

    private static Statement securedStatement(final Model sm) {
        return sm.listStatements(S, P, (RDFNode) null).nextStatement();
    }

    // ---- changeLiteralObject(primitive): typed literal ----

    @Test
    public void changeLiteralObject_deniedWhenTypedCreateDenied() {
        final Model base = ModelFactory.createDefaultModel();
        base.add(S, P, "old");
        // The base writes "5"^^xsd:int; denying that typed node must block the change.
        final RuleEvaluator eval = new RuleEvaluator().denyCreate(triple(base.createTypedLiteral(5)));
        try {
            securedStatement(secure(base, eval)).changeLiteralObject(5);
            fail("changeLiteralObject must authorize the typed literal the base writes");
        } catch (final UpdateDeniedException expected) {
            // correct
        }
        assertTrue("object must be unchanged after a denied change", base.contains(S, P, "old"));
    }

    @Test
    public void changeLiteralObject_notBlockedByStringLiteralRule() {
        final Model base = ModelFactory.createDefaultModel();
        base.add(S, P, "old");
        // Denying the xsd:string "5" (the node the OLD code wrongly checked) must
        // NOT block the change, because the base writes "5"^^xsd:int.
        final RuleEvaluator eval = new RuleEvaluator().denyCreate(triple(base.createLiteral("5")));
        securedStatement(secure(base, eval)).changeLiteralObject(5);
        assertTrue("the typed literal should have been written", base.contains(S, P, base.createTypedLiteral(5)));
    }

    @Test
    public void changeLiteralObject_permitted_writesTypedLiteral() {
        final Model base = ModelFactory.createDefaultModel();
        base.add(S, P, "old");
        securedStatement(secure(base, new RuleEvaluator())).changeLiteralObject(5);
        assertTrue(base.contains(S, P, base.createTypedLiteral(5)));
    }

    // ---- createStatement(s, p, String): string literal ----

    @Test
    public void createStatement_deniedWhenStringLiteralCreateDenied() {
        final Model base = ModelFactory.createDefaultModel();
        // The base stores a string literal "foo"; denying that must block the create.
        final RuleEvaluator eval = new RuleEvaluator().denyCreate(triple(base.createLiteral("foo")));
        try {
            secure(base, eval).createStatement(S, P, "foo");
            fail("createStatement(s,p,String) must authorize the string literal, not a URI");
        } catch (final AddDeniedException expected) {
            // correct
        }
    }

    @Test
    public void createStatement_notBlockedByUriRule() {
        final Model base = ModelFactory.createDefaultModel();
        // Denying the URI node <foo> (what the OLD code wrongly checked) must NOT
        // block createStatement, because the base stores a string literal.
        final RuleEvaluator eval = new RuleEvaluator().denyCreate(triple(ResourceFactory.createProperty("foo")));
        final Statement stmt = secure(base, eval).createStatement(S, P, "foo");
        assertNotNull(stmt);
        assertTrue("object should be a literal", stmt.getObject().isLiteral());
    }

    @Test
    public void createStatement_permitted_returnsStringLiteral() {
        final Model base = ModelFactory.createDefaultModel();
        final Statement stmt = secure(base, new RuleEvaluator()).createStatement(S, P, "foo");
        assertNotNull(stmt);
        assertTrue(stmt.getObject().isLiteral());
        assertTrue("foo".equals(stmt.getObject().asLiteral().getString()));
    }
}
