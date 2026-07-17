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
package org.apache.jena.permissions.model.impl;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.SecuredItem;
import org.apache.jena.permissions.SecurityEvaluator.Action;
import org.apache.jena.permissions.model.SecuredModel;
import org.apache.jena.permissions.model.SecuredStatement;
import org.apache.jena.permissions.utils.PermStatementFilter;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.shared.AuthenticationRequiredException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.UpdateDeniedException;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.NiceIterator;

/**
 * A secured StatementIterator implementation
 */
public class SecuredStatementIterator implements StmtIterator {

    private class PermStatementMap implements Function<Statement, Statement> {
        private final SecuredModel securedModel;

        public PermStatementMap(final SecuredModel securedModel) {
            this.securedModel = securedModel;
        }

        @Override
        public SecuredStatement apply(final Statement o) {
            return SecuredStatementImpl.getInstance(securedModel, o);
        }
    }

    /**
     * Authorize removing {@code triple} through a secured iterator: the graph
     * must be updatable and the triple deletable — the same checks
     * {@code SecuredItemImpl.checkUpdate()} / {@code checkDelete(Triple)}
     * apply to every other delete path, which are not reachable from outside
     * the impl classes. Passing {@link Triple#ANY} means "any triple could be
     * the one removed" and therefore requires blanket delete rights.
     */
    static void checkRemove(final SecuredItem securedItem, final Triple triple)
            throws UpdateDeniedException, DeleteDeniedException, AuthenticationRequiredException {
        if (!securedItem.canUpdate()) {
            throw new UpdateDeniedException(SecuredItem.Util.modelPermissionMsg(securedItem.getModelNode()));
        }
        if (!securedItem.canDelete(triple)) {
            throw new DeleteDeniedException(SecuredItem.Util.triplePermissionMsg(securedItem.getModelNode()), triple);
        }
    }

    private final ExtendedIterator<Statement> iter;
    private final SecuredModel securedModel;
    // The statement last returned by next(); the one remove() must authorize.
    private Statement current;

    /**
     * Constructor.
     *
     * @param securedModel The item providing the security context.
     * @param wrapped      The iterator to wrap.
     */
    public SecuredStatementIterator(final SecuredModel securedModel, final ExtendedIterator<Statement> wrapped) {
        this.securedModel = securedModel;
        final PermStatementFilter filter = new PermStatementFilter(new Action[] { Action.Read }, securedModel);
        final PermStatementMap map1 = new PermStatementMap(securedModel);
        iter = wrapped.filterKeep(filter).mapWith(map1);
    }

    /**
     * Wrap this iterator — not the inner chain — so that iterators derived via
     * andThen/filterKeep/filterDrop/mapWith route remove() back through the
     * permission checks below. Handing out the inner chain would let
     * {@code listStatements().filterKeep(s -> true).removeNext()} delete
     * unchecked (the escape hatch around the H3 fix). An explicit delegating
     * wrapper is required: {@code WrappedIterator.create(this)} returns its
     * argument unchanged for an ExtendedIterator, which would recurse straight
     * back into these methods.
     */
    private ExtendedIterator<Statement> wrapThis() {
        return new NiceIterator<Statement>() {
            @Override
            public boolean hasNext() {
                return SecuredStatementIterator.this.hasNext();
            }

            @Override
            public Statement next() {
                return SecuredStatementIterator.this.next();
            }

            @Override
            public void remove() {
                SecuredStatementIterator.this.remove();
            }

            @Override
            public void close() {
                SecuredStatementIterator.this.close();
            }
        };
    }

    @Override
    public <X extends Statement> ExtendedIterator<Statement> andThen(final Iterator<X> other) {
        return wrapThis().andThen(other);
    }

    @Override
    public void close() {
        iter.close();
    }

    @Override
    public ExtendedIterator<Statement> filterDrop(final Predicate<Statement> f) {
        return wrapThis().filterDrop(f);
    }

    @Override
    public ExtendedIterator<Statement> filterKeep(final Predicate<Statement> f) {
        return wrapThis().filterKeep(f);
    }

    @Override
    public boolean hasNext() {
        return iter.hasNext();
    }

    @Override
    public <U> ExtendedIterator<U> mapWith(final Function<Statement, U> map1) {
        return wrapThis().mapWith(map1);
    }

    @Override
    public Statement next() {
        current = iter.next();
        return current;
    }

    @Override
    public Statement nextStatement() throws NoSuchElementException {
        return next();
    }

    /**
     * Remove the statement last returned by {@link #next()} from the
     * underlying model.
     * <p>
     * H3: this used to delegate straight to the wrapped iterator, whose
     * {@code remove()} (a live model iterator, e.g. {@code StmtIteratorImpl})
     * deletes the current statement from the base model with no permission
     * check — so any principal that could list statements (Read) could delete
     * every statement it saw. The removal is now authorized exactly as
     * {@code SecuredStatementImpl.remove()} authorizes a delete: Update on the
     * graph plus Delete on the specific triple.
     *
     * @sec.graph Update
     * @sec.triple Delete on the statement last returned by next()
     * @throws UpdateDeniedException           if the graph may not be updated.
     * @throws DeleteDeniedException           if the statement may not be
     *                                         deleted.
     * @throws AuthenticationRequiredException if user is not authenticated and
     *                                         is required to be.
     */
    @Override
    public void remove() throws UpdateDeniedException, DeleteDeniedException, AuthenticationRequiredException {
        if (current == null) {
            throw new IllegalStateException("remove() may only be called once after next()");
        }
        checkRemove(securedModel, current.asTriple());
        iter.remove();
        current = null;
    }

    /**
     * @sec.graph Update
     * @sec.triple Delete on the returned statement
     * @throws UpdateDeniedException           if the graph may not be updated.
     * @throws DeleteDeniedException           if the statement may not be
     *                                         deleted.
     * @throws AuthenticationRequiredException if user is not authenticated and
     *                                         is required to be.
     */
    @Override
    public Statement removeNext() {
        final Statement result = next();
        remove();
        return result;
    }

    @Override
    public List<Statement> toList() {
        return iter.toList();
    }

    @Override
    public Set<Statement> toSet() {
        return iter.toSet();
    }
}
