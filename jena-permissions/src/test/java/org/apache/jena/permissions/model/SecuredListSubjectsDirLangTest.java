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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.Test;

/**
 * Regression tests for {@code listSubjectsWithProperty(Property, String, String,
 * String)} (L1) — the Jena 6.1 RDF-1.2 lang+direction overload that the fork
 * had left as a {@code return null;} stub (an NPE hazard / silent no-results).
 * It must now return a read-filtered iterator matching the directional literal.
 */
public class SecuredListSubjectsDirLangTest {

    private static final Resource S = ResourceFactory.createResource("http://example.com/s");
    private static final Property P = ResourceFactory.createProperty("http://example.com/p");

    private static RDFNode dirLang(final Model m, final String lex, final String lang, final String dir) {
        return m.getRDFNode(NodeFactory.createLiteralDirLang(lex, lang, dir));
    }

    private static Model baseWithSubject() {
        final Model m = ModelFactory.createDefaultModel();
        m.add(S, P, dirLang(m, "hello", "en", "ltr"));
        return m;
    }

    private static SecurityEvaluator allowAll() {
        return evaluator(null);
    }

    /** Allows everything, except (when {@code deniedRead} is non-null) Read of that one triple. */
    private static SecurityEvaluator evaluator(final Triple deniedRead) {
        return new SecurityEvaluator() {
            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI) {
                return true;
            }

            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI,
                    final Triple triple) {
                if (action == Action.Read && deniedRead != null) {
                    final boolean wildcard = triple.getSubject().equals(Node.ANY)
                            || triple.getPredicate().equals(Node.ANY) || triple.getObject().equals(Node.ANY);
                    // A read restriction exists, so a wildcard check must fail closed
                    // (forcing per-triple filtering rather than a blanket allow).
                    return wildcard ? false : !triple.equals(deniedRead);
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

    private static Model secure(final Model base, final SecurityEvaluator eval) {
        return Factory.getInstance(eval, "http://example.com/model", base);
    }

    @Test
    public void returnsSubject_whenReadable() {
        final Model base = baseWithSubject();
        final ResIterator it = secure(base, allowAll()).listSubjectsWithProperty(P, "hello", "en", "ltr");
        assertNotNull("must not return null (was a stub)", it);
        assertTrue("the matching subject must be returned", it.toSet().contains(S));
    }

    @Test
    public void filtersUnreadableSubject() {
        final Model base = baseWithSubject();
        final Triple denied = Triple.create(S.asNode(), P.asNode(),
                NodeFactory.createLiteralDirLang("hello", "en", "ltr"));
        final ResIterator it = secure(base, evaluator(denied)).listSubjectsWithProperty(P, "hello", "en", "ltr");
        assertFalse("a subject whose only matching triple is unreadable must be filtered out", it.hasNext());
    }

    @Test
    public void doesNotMatchDifferentLang() {
        final Model base = baseWithSubject(); // stored with lang "en"
        final ResIterator it = secure(base, allowAll()).listSubjectsWithProperty(P, "hello", "fr", "ltr");
        assertFalse("a different language tag must not match", it.hasNext());
    }
}
