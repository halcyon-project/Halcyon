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
import org.apache.jena.permissions.model.SecuredModel;
import org.apache.jena.permissions.model.SecuredRDFNode;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.shared.AuthenticationRequiredException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.UpdateDeniedException;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.NiceIterator;

/**
 * A secured RDFNode iterator implementation
 */
public class SecuredNodeIterator<T extends RDFNode> implements NodeIterator {
    private class PermNodeMap<N extends RDFNode> implements Function<N, RDFNode> {
        private final SecuredModel securedModel;

        public PermNodeMap(final SecuredModel securedModel) {
            this.securedModel = securedModel;
        }

        @Override
        public SecuredRDFNode apply(final RDFNode o) {
            return SecuredRDFNodeImpl.getInstance(securedModel, o);
        }
    }

    private final ExtendedIterator<RDFNode> iter;
    private final SecuredModel securedModel;
    // True when the wrapped iterator itself enforces the exact per-triple
    // permissions on removal, so remove() may delegate without the blanket
    // check below.
    private final boolean removalChecked;

    /**
     * Constructor
     *
     * @param securedItem the item defining the security context
     * @param wrapped     the iterator to be wrapped.
     */
    SecuredNodeIterator(final SecuredModel securedModel, final ExtendedIterator<T> wrapped) {
        this(securedModel, wrapped, false);
    }

    /**
     * Constructor
     *
     * @param securedItem    the item defining the security context
     * @param wrapped        the iterator to be wrapped.
     * @param removalChecked true only when the wrapped iterator already
     *                       enforces Update/Delete on the exact triple(s) a
     *                       removal mutates (the container iterator built over
     *                       secured statements does — see
     *                       {@code SecuredContainerImpl.iterator()}); false for
     *                       iterators over plain base data, whose removal is
     *                       then gated on blanket delete rights by
     *                       {@link #remove()}.
     */
    SecuredNodeIterator(final SecuredModel securedModel, final ExtendedIterator<T> wrapped,
            final boolean removalChecked) {
        this.securedModel = securedModel;
        this.removalChecked = removalChecked;
        final PermNodeMap<T> map1 = new PermNodeMap<>(securedModel);
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
    private ExtendedIterator<RDFNode> wrapThis() {
        return new NiceIterator<RDFNode>() {
            @Override
            public boolean hasNext() {
                return SecuredNodeIterator.this.hasNext();
            }

            @Override
            public RDFNode next() {
                return SecuredNodeIterator.this.next();
            }

            @Override
            public void remove() {
                SecuredNodeIterator.this.remove();
            }

            @Override
            public void close() {
                SecuredNodeIterator.this.close();
            }
        };
    }

    @Override
    public <X extends RDFNode> ExtendedIterator<RDFNode> andThen(final Iterator<X> other) {
        return wrapThis().andThen(other);
    }

    @Override
    public void close() {
        iter.close();
    }

    @Override
    public ExtendedIterator<RDFNode> filterDrop(final Predicate<RDFNode> f) {
        return wrapThis().filterDrop(f);
    }

    @Override
    public ExtendedIterator<RDFNode> filterKeep(final Predicate<RDFNode> f) {
        return wrapThis().filterKeep(f);
    }

    @Override
    public boolean hasNext() {
        return iter.hasNext();
    }

    @Override
    public <U> ExtendedIterator<U> mapWith(final Function<RDFNode, U> map1) {
        return wrapThis().mapWith(map1);
    }

    @Override
    public RDFNode next() {
        return iter.next();
    }

    @Override
    public RDFNode nextNode() throws NoSuchElementException {
        return next();
    }

    /**
     * Remove from the underlying model whatever the wrapped iterator's
     * {@code remove()} removes for the node last returned by {@link #next()}.
     * <p>
     * M2 (sibling of H3): this used to delegate with no permission check, so a
     * Read-only principal could delete through e.g.
     * {@code listObjectsOfProperty(s, p).remove()}. Unlike a statement
     * iterator, a node does not identify the triple(s) the base iterator would
     * remove, so no per-triple check can be built here: unless the wrapped
     * iterator performs its own exact checks ({@code removalChecked}, the
     * container path), removal requires Update on the graph and Delete over
     * any triple ({@link Triple#ANY}), failing closed for per-triple
     * restricted principals.
     *
     * @sec.graph Update
     * @sec.triple Delete over Triple.ANY (unless the wrapped iterator enforces
     *             exact per-triple checks itself)
     * @throws UpdateDeniedException           if the graph may not be updated.
     * @throws DeleteDeniedException           if blanket delete rights are
     *                                         missing.
     * @throws AuthenticationRequiredException if user is not authenticated and
     *                                         is required to be.
     */
    @Override
    public void remove() throws UpdateDeniedException, DeleteDeniedException, AuthenticationRequiredException {
        if (!removalChecked) {
            SecuredStatementIterator.checkRemove(securedModel, Triple.ANY);
        }
        iter.remove();
    }

    /**
     * @sec.graph Update
     * @sec.triple Delete over Triple.ANY (unless the wrapped iterator enforces
     *             exact per-triple checks itself)
     * @throws UpdateDeniedException           if the graph may not be updated.
     * @throws DeleteDeniedException           if blanket delete rights are
     *                                         missing.
     * @throws AuthenticationRequiredException if user is not authenticated and
     *                                         is required to be.
     */
    @Override
    public RDFNode removeNext() {
        final RDFNode result = next();
        remove();
        return result;
    }

    @Override
    public List<RDFNode> toList() {
        return iter.toList();
    }

    @Override
    public Set<RDFNode> toSet() {
        return iter.toSet();
    }
}
