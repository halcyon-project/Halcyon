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
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.model.SecuredModel;
import org.apache.jena.permissions.model.SecuredResource;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.AuthenticationRequiredException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.UpdateDeniedException;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.NiceIterator;

public class SecuredResIterator implements ResIterator {

    /**
     * Maps a Resource to a secured resource
     *
     */
    private class PermResourceMap implements Function<Resource, Resource> {
        private final SecuredModel securedModel;

        /**
         * Constructor.
         *
         * @param securedModel the secured model in which the resources will be created.
         */
        public PermResourceMap(final SecuredModel securedModel) {
            this.securedModel = securedModel;
        }

        @Override
        public SecuredResource apply(final Resource o) {
            return SecuredResourceImpl.getInstance(securedModel, o);
        }
    }

    private final ExtendedIterator<Resource> iter;
    private final SecuredModel securedModel;

    /**
     * Constructor.
     *
     * @param securedModel The model in which resources will be constructed
     * @param wrapped      the Resource iterator.
     */
    public SecuredResIterator(final SecuredModel securedModel, final ExtendedIterator<Resource> wrapped) {
        this.securedModel = securedModel;
        final PermResourceMap map1 = new PermResourceMap(securedModel);
        iter = wrapped.mapWith(map1);
    }

    /**
     * Wrap this iterator — not the inner chain — so that iterators derived via
     * andThen/filterKeep/filterDrop/mapWith route remove() back through the
     * permission checks below rather than reaching the base iterator directly.
     * An explicit delegating wrapper is required: {@code
     * WrappedIterator.create(this)} returns its argument unchanged for an
     * ExtendedIterator, which would recurse straight back into these methods.
     */
    private ExtendedIterator<Resource> wrapThis() {
        return new NiceIterator<Resource>() {
            @Override
            public boolean hasNext() {
                return SecuredResIterator.this.hasNext();
            }

            @Override
            public Resource next() {
                return SecuredResIterator.this.next();
            }

            @Override
            public void remove() {
                SecuredResIterator.this.remove();
            }

            @Override
            public void close() {
                SecuredResIterator.this.close();
            }
        };
    }

    @Override
    public <X extends Resource> ExtendedIterator<Resource> andThen(final Iterator<X> other) {
        return wrapThis().andThen(other);
    }

    @Override
    public void close() {
        iter.close();
    }

    @Override
    public ExtendedIterator<Resource> filterDrop(final Predicate<Resource> f) {
        return wrapThis().filterDrop(f);
    }

    @Override
    public ExtendedIterator<Resource> filterKeep(final Predicate<Resource> f) {
        return wrapThis().filterKeep(f);
    }

    @Override
    public boolean hasNext() {
        return iter.hasNext();
    }

    @Override
    public <U> ExtendedIterator<U> mapWith(final Function<Resource, U> map1) {
        return wrapThis().mapWith(map1);
    }

    @Override
    public Resource next() {
        return iter.next();
    }

    @Override
    public Resource nextResource() {
        return next();
    }

    /**
     * Remove from the underlying model whatever the wrapped iterator's
     * {@code remove()} removes for the resource last returned by
     * {@link #next()}.
     * <p>
     * M2 (sibling of H3): this used to delegate with no permission check on
     * the listSubjects/listResourcesWithProperty paths. A resource does not
     * identify the triple(s) the base iterator would remove, so no per-triple
     * check can be built here: removal requires Update on the graph and Delete
     * over any triple ({@link Triple#ANY}), failing closed for per-triple
     * restricted principals.
     *
     * @sec.graph Update
     * @sec.triple Delete over Triple.ANY
     * @throws UpdateDeniedException           if the graph may not be updated.
     * @throws DeleteDeniedException           if blanket delete rights are
     *                                         missing.
     * @throws AuthenticationRequiredException if user is not authenticated and
     *                                         is required to be.
     */
    @Override
    public void remove() throws UpdateDeniedException, DeleteDeniedException, AuthenticationRequiredException {
        SecuredStatementIterator.checkRemove(securedModel, Triple.ANY);
        iter.remove();
    }

    /**
     * @sec.graph Update
     * @sec.triple Delete over Triple.ANY
     * @throws UpdateDeniedException           if the graph may not be updated.
     * @throws DeleteDeniedException           if blanket delete rights are
     *                                         missing.
     * @throws AuthenticationRequiredException if user is not authenticated and
     *                                         is required to be.
     */
    @Override
    public Resource removeNext() {
        final Resource result = next();
        remove();
        return result;
    }

    @Override
    public List<Resource> toList() {
        return iter.toList();
    }

    @Override
    public Set<Resource> toSet() {
        return iter.toSet();
    }
}
