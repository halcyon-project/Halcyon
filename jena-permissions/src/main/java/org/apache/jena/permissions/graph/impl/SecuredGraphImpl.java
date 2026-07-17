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
package org.apache.jena.permissions.graph.impl;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.jena.atlas.lib.Sync;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.TransactionHandler;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.impl.GraphWithPerform;
import org.apache.jena.sparql.core.NamedGraph;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.permissions.SecurityEvaluator.Action;
import org.apache.jena.permissions.graph.SecuredGraph;
import org.apache.jena.permissions.graph.SecuredGraphEventManager;
import org.apache.jena.permissions.graph.SecuredPrefixMapping;
import org.apache.jena.permissions.impl.ItemHolder;
import org.apache.jena.permissions.impl.SecuredItemImpl;
import org.apache.jena.permissions.utils.PermTripleFilter;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.AuthenticationRequiredException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.ReadDeniedException;
import org.apache.jena.shared.UpdateDeniedException;
import org.apache.jena.util.iterator.ExtendedIterator;

/**
 * Implementation of SecuredGraph to be used by a SecuredItemInvoker proxy.
 */
public class SecuredGraphImpl extends SecuredItemImpl implements SecuredGraph, GraphWithPerform {

    // the prefixMapping for this graph. Initialized eagerly: this used to be
    // lazily double-checked-locked on a non-volatile field, which under the
    // JMM lets a second thread observe a non-null but not-yet-fully-published
    // reference (L12).
    private final SecuredPrefixMapping prefixMapping;
    // the item holder that contains this SecuredGraph
    private final ItemHolder<Graph, SecuredGraphImpl> holder;

    private final SecuredGraphEventManager eventManager;

    /**
     * Constructor
     *
     * @param securityEvaluator The security evaluator to use
     * @param graphIRI          The IRI for the graph
     * @param holder            The item holder that will contain this SecuredGraph.
     */
    SecuredGraphImpl(final SecurityEvaluator securityEvaluator, final String modelURI,
            final ItemHolder<Graph, SecuredGraphImpl> holder) {
        super(securityEvaluator, modelURI, holder);
        this.holder = holder;
        this.eventManager = new SecuredGraphEventManager(this, holder.getBaseItem(),
                holder.getBaseItem().getEventManager());
        this.prefixMapping = org.apache.jena.permissions.graph.impl.Factory.getInstance(this,
                holder.getBaseItem().getPrefixMapping());
    }

    /**
     * @sec.graph Update
     * @sec.triple Create
     * @throws AddDeniedException
     * @throws UpdateDeniedException           if the graph can not be updated.
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public void add(final Triple t) throws AddDeniedException, UpdateDeniedException, AuthenticationRequiredException {
        checkUpdate();
        checkCreate(t);
        holder.getBaseItem().add(t);
    }

    /**
     * Add a triple without firing add notifications ({@link GraphWithPerform}).
     * <p>
     * This must be secured with the same checks as {@link #add(Triple)}: the
     * proxy exposes {@code GraphWithPerform} because it inherits the base
     * graph's interfaces, and Jena bulk paths (e.g. {@code GraphUtil.addInto},
     * and hence {@code Model.add(Model)} / {@code RDFDataMgr.read} into this
     * graph) call {@code performAdd} directly rather than {@link #add(Triple)}.
     * Without this override those paths would write to the base graph
     * unchecked.
     *
     * @sec.graph Update
     * @sec.triple Create
     * @throws AddDeniedException
     * @throws UpdateDeniedException           if the graph can not be updated.
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public void performAdd(final Triple t)
            throws AddDeniedException, UpdateDeniedException, AuthenticationRequiredException {
        checkUpdate();
        checkCreate(t);
        final Graph base = holder.getBaseItem();
        if (base instanceof GraphWithPerform) {
            ((GraphWithPerform) base).performAdd(t);
        } else {
            base.add(t);
        }
    }

    /**
     * @sec.graph Update
     * @sec.triple Delete for every triple
     * @throws DeleteDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public void clear() throws UpdateDeniedException, AuthenticationRequiredException {
        checkUpdate();
        if (!canDelete(Triple.ANY)) {
            ExtendedIterator<Triple> iter = holder.getBaseItem().find(Triple.ANY);
            while (iter.hasNext()) {
                checkDelete(iter.next());
            }
        }
        holder.getBaseItem().clear();
    }

    @Override
    public void close() {
        holder.getBaseItem().close();
    }

    /**
     * @sec.graph Read
     * @sec.triple Read
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public boolean contains(final Node s, final Node p, final Node o)
            throws ReadDeniedException, AuthenticationRequiredException {
        return contains(Triple.create(s, p, o));
    }

    /**
     * @sec.graph Read
     * @sec.triple Read
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public boolean contains(final Triple t) throws ReadDeniedException, AuthenticationRequiredException {
        if (checkSoftRead()) {
            if (canRead(t)) {
                return holder.getBaseItem().contains(t);
            }
            final ExtendedIterator<Triple> iter = holder.getBaseItem().find(t);
            try {
                while (iter.hasNext()) {
                    if (canRead(iter.next())) {
                        return true;
                    }
                }
                return false;
            } finally {
                iter.close();
            }
        }
        return false;
    }

    /**
     * @sec.graph Update
     * @sec.triple Delete
     * @throws DeleteDeniedException
     * @throws UpdateDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public void delete(final Triple t) throws DeleteDeniedException, AuthenticationRequiredException {
        checkUpdate();
        checkDelete(t);
        holder.getBaseItem().delete(t);
    }

    /**
     * Delete a triple without firing delete notifications
     * ({@link GraphWithPerform}).
     * <p>
     * This must be secured with the same checks as {@link #delete(Triple)}: the
     * proxy exposes {@code GraphWithPerform} because it inherits the base
     * graph's interfaces, and Jena bulk paths (e.g. {@code GraphUtil.deleteFrom},
     * and hence {@code Model.remove(Model)}) call {@code performDelete} directly
     * rather than {@link #delete(Triple)}. Without this override those paths
     * would delete from the base graph unchecked.
     *
     * @sec.graph Update
     * @sec.triple Delete
     * @throws DeleteDeniedException
     * @throws UpdateDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public void performDelete(final Triple t)
            throws DeleteDeniedException, UpdateDeniedException, AuthenticationRequiredException {
        checkUpdate();
        checkDelete(t);
        final Graph base = holder.getBaseItem();
        if (base instanceof GraphWithPerform) {
            ((GraphWithPerform) base).performDelete(t);
        } else {
            base.delete(t);
        }
    }

    /**
     * @sec.graph Read
     * @sec.triple Read, otherwise filtered from iterator.
     *
     *             if {@link SecurityEvaluator#isHardReadError()} is true and the
     *             user does not have read access then an empty iterator will be
     *             returned.
     *
     * @throws ReadDeniedException             on read not allowed
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public ExtendedIterator<Triple> find() throws ReadDeniedException, AuthenticationRequiredException {
        return createIterator(() -> holder.getBaseItem().find(Triple.ANY),
                () -> new PermTripleFilter(Action.Read, this));
    }

    /**
     * @sec.graph Read
     * @sec.triple Read, otherwise filtered from iterator.
     *
     *             if {@link SecurityEvaluator#isHardReadError()} is true and the
     *             user does not have read access then an empty iterator will be
     *             returned.
     *
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public ExtendedIterator<Triple> find(final Node s, final Node p, final Node o)
            throws ReadDeniedException, AuthenticationRequiredException {
        return createIterator(() -> holder.getBaseItem().find(s, p, o), () -> new PermTripleFilter(Action.Read, this));
    }

    /**
     * @sec.graph Read
     * @sec.triple Read, otherwise filtered from iterator.
     *
     *             if {@link SecurityEvaluator#isHardReadError()} is true then an
     *             empty iterator will be returned.
     *
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public ExtendedIterator<Triple> find(final Triple t) throws ReadDeniedException, AuthenticationRequiredException {
        return createIterator(() -> holder.getBaseItem().find(t), () -> new PermTripleFilter(Action.Read, this));
    }

    @Override
    public SecuredGraphEventManager getEventManager() {
        return eventManager;
    }

    /**
     * The base graph's name when the base is a {@link NamedGraph} (e.g. a TDB2
     * GraphView). Pure metadata — the name is the identifier the caller
     * already used to reach this graph, carrying no triple data — so no
     * permission check applies. Declared here because the proxy exposes every
     * base interface and the invoker fails closed (L12) on methods the secured
     * wrapper does not implement.
     */
    public Node getGraphName() {
        return ((NamedGraph) holder.getBaseItem()).getGraphName();
    }

    @Override
    public SecuredPrefixMapping getPrefixMapping() {
        return prefixMapping;
    }

    @Override
    public TransactionHandler getTransactionHandler() {
        return holder.getBaseItem().getTransactionHandler();
    }

    /**
     * Flush the base graph to stable storage when the base supports
     * {@link Sync} (e.g. TDB-backed graphs). Operational only — it neither
     * reads nor mutates triple data — so no permission check applies; declared
     * for the same fail-closed-invoker reason as {@link #getGraphName()}.
     */
    public void sync() {
        ((Sync) holder.getBaseItem()).sync();
    }

    @Override
    public boolean isClosed() {
        return holder.getBaseItem().isClosed();
    }

    /**
     * @sec.graph Read
     *
     *            If {@link SecurityEvaluator#isHardReadError()} is false then this
     *            method will return 0.
     *
     * @throws ReadDeniedException             if graph can not be read.
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public boolean isEmpty() throws ReadDeniedException, AuthenticationRequiredException {
        if (checkSoftRead()) {
            if (canRead(Triple.ANY)) {
                return holder.getBaseItem().isEmpty();
            }
            // No blanket read: empty from the caller's view iff no readable
            // triple exists — consistent with the read-filtered find()/size().
            final ExtendedIterator<Triple> iter = find(Triple.ANY);
            try {
                return !iter.hasNext();
            } finally {
                iter.close();
            }
        }
        return true;
    }

    /**
     * @sec.graph Read
     *
     *            If {@link SecurityEvaluator#isHardReadError()} is false then this
     *            method will return false unless {@code g} is empty.
     *
     * @throws ReadDeniedException             if graph can not be read.
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public boolean isIsomorphicWith(final Graph g) throws ReadDeniedException, AuthenticationRequiredException {
        if (checkSoftRead()) {
            if (g.size() != holder.getBaseItem().size()) {
                return false;
            }
            final Triple t = Triple.create(Node.ANY, Node.ANY, Node.ANY);
            if (!canRead(t)) {
                final ExtendedIterator<Triple> iter = g.find(t);
                while (iter.hasNext()) {
                    if (!checkRead(iter.next())) {
                        return false;
                    }
                }
            }
            return holder.getBaseItem().isIsomorphicWith(g);
        }
        return g.isEmpty();
    }

    /**
     * @sec.graph Update
     * @sec.triple Delete (s, p, o )
     * @throws DeleteDeniedException
     * @throws UpdateDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public void remove(Node s, Node p, Node o)
            throws UpdateDeniedException, DeleteDeniedException, AuthenticationRequiredException {
        checkUpdate();
        Triple t = Triple.create(s, p, o);
        if (t.isConcrete()) {
            checkDelete(t);
        } else {
            ExtendedIterator<Triple> iter = holder.getBaseItem().find(t);
            while (iter.hasNext()) {
                checkDelete(iter.next());
            }
        }
        holder.getBaseItem().remove(s, p, o);
    }

    /**
     * @sec.graph Read
     *
     *            If {@link SecurityEvaluator#isHardReadError()} is false then this
     *            method will return 0.
     *
     * @throws ReadDeniedException             if graph can not be read.
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public int size() throws ReadDeniedException, AuthenticationRequiredException {
        if (checkSoftRead()) {
            if (canRead(Triple.ANY)) {
                return holder.getBaseItem().size();
            }
            return IteratorUtils.size(find(Triple.ANY));
        }
        return 0;
    }
}