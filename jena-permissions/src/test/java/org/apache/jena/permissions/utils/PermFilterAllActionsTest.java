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
package org.apache.jena.permissions.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.permissions.SecurityEvaluator.Action;
import org.apache.jena.permissions.graph.SecuredGraph;
import org.apache.jena.permissions.model.SecuredModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.Test;

/**
 * Regression tests for M7: {@code PermTripleFilter}/{@code PermStatementFilter}
 * are documented to keep only items the user may perform <em>all</em> the
 * requested actions on, but used {@code evaluateAny} (OR). With a multi-action
 * filter ({@code Delete, Read}) that admitted a Delete-but-not-Read triple,
 * leaking its content to a delete-event listener. They must now AND the actions.
 */
public class PermFilterAllActionsTest {

    private static final Resource S1 = ResourceFactory.createResource("http://example.com/s1");
    private static final Resource S2 = ResourceFactory.createResource("http://example.com/s2");
    private static final Property P = ResourceFactory.createProperty("http://example.com/p");
    private static final Resource O1 = ResourceFactory.createResource("http://example.com/o1");
    private static final Resource O2 = ResourceFactory.createResource("http://example.com/o2");

    private static final Triple READABLE = Triple.create(S1.asNode(), P.asNode(), O1.asNode());
    private static final Triple DELETE_ONLY = Triple.create(S2.asNode(), P.asNode(), O2.asNode());

    /** Allows every action, except Read of the {@code DELETE_ONLY} triple. */
    private static SecurityEvaluator eval() {
        return new SecurityEvaluator() {
            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI) {
                return true;
            }

            @Override
            public boolean evaluate(final Object principal, final Action action, final Node graphIRI,
                    final Triple triple) {
                return !(action == Action.Read && triple.equals(DELETE_ONLY));
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

    @Test
    public void tripleFilter_multiAction_requiresAll() {
        final SecuredGraph sg = Factory.getInstance(eval(), "http://example.com/g", GraphFactory.createDefaultGraph());
        final PermTripleFilter delFilter = new PermTripleFilter(new Action[] { Action.Delete, Action.Read }, sg);
        // Delete=yes, Read=no -> must be filtered out (was admitted by evaluateAny).
        assertFalse("a Delete-but-not-Read triple must not pass a {Delete,Read} filter", delFilter.test(DELETE_ONLY));
        // Delete=yes, Read=yes -> kept.
        assertTrue(delFilter.test(READABLE));
    }

    @Test
    public void tripleFilter_singleAction_unchanged() {
        final SecuredGraph sg = Factory.getInstance(eval(), "http://example.com/g", GraphFactory.createDefaultGraph());
        final PermTripleFilter readFilter = new PermTripleFilter(Action.Read, sg);
        assertTrue(readFilter.test(READABLE));
        assertFalse(readFilter.test(DELETE_ONLY));
    }

    @Test
    public void statementFilter_multiAction_requiresAll() {
        final Model base = ModelFactory.createDefaultModel();
        final SecuredModel sm = Factory.getInstance(eval(), "http://example.com/m", base);
        final PermStatementFilter delFilter = new PermStatementFilter(new Action[] { Action.Delete, Action.Read }, sm);
        final Statement readable = base.createStatement(S1, P, O1);
        final Statement deleteOnly = base.createStatement(S2, P, O2);
        assertFalse("a Delete-but-not-Read statement must not pass a {Delete,Read} filter",
                delFilter.test(deleteOnly));
        assertTrue(delFilter.test(readable));
    }
}
