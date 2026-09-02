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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.MockSecurityEvaluator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.UpdateDeniedException;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.junit.Before;
import org.junit.Test;

/**
 * H3 / M2: iterators handed out by a secured model must not let a principal
 * delete triples through {@code remove()}/{@code removeNext()} without Update
 * and Delete permission. H3 is the statement iterator
 * ({@code listStatements()}/{@code listProperties()}), which authorizes the
 * exact statement last returned by {@code next()}; M2 is the node/resource
 * iterators ({@code listObjects()}/{@code listSubjects()}), where a node
 * cannot be mapped back to the triple(s) a removal would mutate, so removal
 * is gated on blanket delete rights and fails closed for per-triple
 * principals.
 */
public class SecuredModelIteratorRemoveTest {

    private Model baseModel;

    @Before
    public void setup() {
        baseModel = ModelFactory.createDefaultModel();
        final Resource s = baseModel.createResource("http://example.com/s");
        final Property p = baseModel.createProperty("http://example.com/p");
        baseModel.add(s, p, "A");
        baseModel.add(s, p, "B");
        baseModel.add(s, p, "C");
    }

    // loggedIn, create, read, update, delete, forceTripleChecks, hardReadError
    private Model secure(final boolean update, final boolean delete, final boolean forceTripleChecks) {
        final MockSecurityEvaluator eval = new MockSecurityEvaluator(true, true, true, update, delete,
                forceTripleChecks, true);
        return Factory.getInstance(eval, "http://example.com/securedModel", baseModel);
    }

    // ---- H3: statement iterator ------------------------------------------

    @Test
    public void statementRemoveNext_deniedWithoutDelete() {
        final StmtIterator it = secure(true, false, false).listStatements();
        try {
            it.removeNext();
            fail("removeNext() must not delete a statement without Delete permission");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertEquals("no statement may have been deleted", 3, baseModel.size());
    }

    @Test
    public void statementRemove_deniedWithoutUpdate() {
        final StmtIterator it = secure(false, true, false).listStatements();
        it.next();
        try {
            it.remove();
            fail("remove() must not delete a statement without Update permission");
        } catch (final UpdateDeniedException expected) {
            // correct
        }
        assertEquals("no statement may have been deleted", 3, baseModel.size());
    }

    @Test
    public void statementRemove_beforeNextIsIllegalState() {
        final StmtIterator it = secure(true, true, false).listStatements();
        try {
            it.remove();
            fail("remove() before next() must fail");
        } catch (final IllegalStateException expected) {
            // correct
        }
        assertEquals(3, baseModel.size());
    }

    @Test
    public void statementRemove_permittedRemoves() {
        final StmtIterator it = secure(true, true, false).listStatements();
        it.next();
        it.remove();
        assertEquals("the statement should have been deleted", 2, baseModel.size());
    }

    @Test
    public void statementRemoveNext_permittedReturnsRemoved() {
        final StmtIterator it = secure(true, true, false).listStatements();
        final Statement removed = it.removeNext();
        assertNotNull(removed);
        assertEquals(2, baseModel.size());
        assertFalse("the returned statement is the removed one", baseModel.contains(removed));
    }

    /**
     * The escape hatch around the H3 fix: a derived iterator
     * (filterKeep/filterDrop/mapWith/andThen) must route remove() back through
     * the permission checks rather than expose the unchecked inner chain.
     */
    @Test
    public void statementDerivedIterator_removeDenied() {
        final ExtendedIterator<Statement> derived = secure(true, false, false).listStatements()
                .filterKeep(st -> true);
        derived.next();
        try {
            derived.remove();
            fail("a derived iterator's remove() must not delete without Delete permission");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertEquals("no statement may have been deleted", 3, baseModel.size());
    }

    // ---- M2: node iterator (listObjects paths) ----------------------------

    @Test
    public void nodeIteratorRemove_deniedWithoutDelete() {
        final NodeIterator it = secure(true, false, false).listObjects();
        it.next();
        try {
            it.remove();
            fail("NodeIterator.remove() must not delete without Delete permission");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertEquals("no triple may have been deleted", 3, baseModel.size());
    }

    @Test
    public void nodeIteratorRemoveNext_deniedWithoutUpdate() {
        final NodeIterator it = secure(false, true, false).listObjects();
        try {
            it.removeNext();
            fail("NodeIterator.removeNext() must not delete without Update permission");
        } catch (final UpdateDeniedException expected) {
            // correct
        }
        assertEquals("no triple may have been deleted", 3, baseModel.size());
    }

    /**
     * The live node-iterator path: unlike {@code listObjects()} (whose mem
     * base is set-materialized and refuses removal anyway),
     * {@code listObjectsOfProperty(s, p)} maps a live statement iterator, so
     * an unguarded remove() really deleted the current triple from the graph.
     */
    @Test
    public void nodeIteratorOfPropertyRemove_deniedWithoutDelete() {
        final Model secured = secure(true, false, false);
        final NodeIterator it = secured.listObjectsOfProperty(secured.getResource("http://example.com/s"),
                secured.getProperty("http://example.com/p"));
        it.next();
        try {
            it.remove();
            fail("NodeIterator.remove() on a live listObjectsOfProperty iterator must not delete without Delete permission");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertEquals("no triple may have been deleted", 3, baseModel.size());
    }

    /**
     * A principal whose Delete rights are per-triple (Triple.ANY evaluates
     * false) must be refused: a node does not identify the triple(s) the base
     * iterator would remove, so no exact check can be built and the iterator
     * fails closed. (remove() is called without next(): with forced triple
     * checks the read filter hides every node, and the permission gate fires
     * before any iterator-state bookkeeping.)
     */
    @Test
    public void nodeIteratorRemove_perTriplePrincipalFailsClosed() {
        final NodeIterator it = secure(true, true, true).listObjects();
        try {
            it.remove();
            fail("NodeIterator.remove() must fail closed for a per-triple principal");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertEquals("no triple may have been deleted", 3, baseModel.size());
    }

    // ---- M2: resource iterator (listSubjects paths) -----------------------

    @Test
    public void resIteratorRemove_deniedWithoutDelete() {
        final ResIterator it = secure(true, false, false).listSubjects();
        it.next();
        try {
            it.remove();
            fail("ResIterator.remove() must not delete without Delete permission");
        } catch (final DeleteDeniedException expected) {
            // correct
        }
        assertEquals("no triple may have been deleted", 3, baseModel.size());
    }

    @Test
    public void resIteratorRemoveNext_deniedWithoutUpdate() {
        final ResIterator it = secure(false, true, false).listSubjects();
        try {
            it.removeNext();
            fail("ResIterator.removeNext() must not delete without Update permission");
        } catch (final UpdateDeniedException expected) {
            // correct
        }
        assertEquals("no triple may have been deleted", 3, baseModel.size());
    }
}
