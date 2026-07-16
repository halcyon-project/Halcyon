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

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.permissions.SecurityEvaluator.Action;
import org.apache.jena.permissions.impl.ItemHolder;
import org.apache.jena.permissions.impl.SecuredItemInvoker;
import org.apache.jena.permissions.model.SecuredContainer;
import org.apache.jena.permissions.model.SecuredModel;
import org.apache.jena.permissions.utils.ContainerFilter;
import org.apache.jena.permissions.utils.PermStatementFilter;
import org.apache.jena.rdf.model.Alt;
import org.apache.jena.rdf.model.Bag;
import org.apache.jena.rdf.model.Container;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Seq;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.AuthenticationRequiredException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.ReadDeniedException;
import org.apache.jena.shared.UpdateDeniedException;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.WrappedIterator;
import org.apache.jena.vocabulary.RDF;

/**
 * Implementation of SecuredContainer to be used by a SecuredItemInvoker proxy.
 */
@SuppressWarnings("all")
public class SecuredContainerImpl extends SecuredResourceImpl implements SecuredContainer {
    /**
     * Constructor
     *
     * @param securedModel the Secured Model to use.
     * @param container    The container to secure.
     * @return The SecuredResource
     */
    public static SecuredContainer getInstance(final SecuredModel securedModel, final Container container) {
        if (securedModel == null) {
            throw new IllegalArgumentException("Secured securedModel may not be null");
        }
        if (container == null) {
            throw new IllegalArgumentException("Container may not be null");
        }

        // check that resource has a securedModel.
        Container goodContainer = container;
        if (goodContainer.getModel() == null) {
            container.asNode();
            goodContainer = securedModel.createBag();
        }

        final ItemHolder<Container, SecuredContainer> holder = new ItemHolder<>(goodContainer);

        final SecuredContainerImpl checker = new SecuredContainerImpl(securedModel, holder);
        // if we are going to create a duplicate proxy, just return this
        // one.
        if (goodContainer instanceof SecuredContainer) {
            if (checker.isEquivalent((SecuredContainer) goodContainer)) {
                return (SecuredContainer) goodContainer;
            }
        }

        return holder.setSecuredItem(new SecuredItemInvoker(container.getClass(), checker));

    }

    // the item holder that contains this SecuredContainer.
    private final ItemHolder<? extends Container, ? extends SecuredContainer> holder;

    /**
     * Constructor
     *
     * @param securedModel the Secured Model to use.
     * @param holder       The item holder that will contain this SecuredContainer
     */
    protected SecuredContainerImpl(final SecuredModel securedModel,
            final ItemHolder<? extends Container, ? extends SecuredContainer> holder) {
        super(securedModel, holder);
        this.holder = holder;
    }

    /**
     * Returns the Object as an RDFNode. If it is a node return it otherwise convert
     * it as a literal
     *
     * @param o the object to convert.
     * @return an RDFNode
     */
    protected RDFNode asObject(Object o) {
        return o instanceof RDFNode ? (RDFNode) o : holder.getBaseItem().getModel().createTypedLiteral(o);
    }

    /**
     * Create an RDFNode (Literal) from a string value and language.
     *
     * @param value    the value
     * @param language the language
     * @return a Literal RDFNode.
     */
    protected RDFNode asLiteral(String value, String language) {
        return holder.getBaseItem().getModel().createLiteral(value, language);
    }

    /**
     * @sec.graph Update
     * @sec.triple Create SecTriple( this, RDF.li, o );
     * @throws UpdateDeniedException
     * @throws AddDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public SecuredContainer add(final boolean o)
            throws AddDeniedException, UpdateDeniedException, AuthenticationRequiredException {
        return add(asObject(o));
    }

    /**
     * @sec.graph Update
     * @sec.triple Create SecTriple( this, RDF.li, o );
     * @throws UpdateDeniedException
     * @throws AddDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public SecuredContainer add(final char o)
            throws AddDeniedException, UpdateDeniedException, AuthenticationRequiredException {
        return add(asObject(o));
    }

    /**
     * @sec.graph Update
     * @sec.triple Create SecTriple( this, RDF.li, o );
     * @throws UpdateDeniedException
     * @throws AddDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public SecuredContainer add(final double o)
            throws AddDeniedException, UpdateDeniedException, AuthenticationRequiredException {
        return add(asObject(o));
    }

    /**
     * @sec.graph Update
     * @sec.triple Create SecTriple( this, RDF.li, o );
     * @throws UpdateDeniedException
     * @throws AddDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public SecuredContainer add(final float o)
            throws AddDeniedException, UpdateDeniedException, AuthenticationRequiredException {
        return add(asObject(o));
    }

    /**
     * @sec.graph Update
     * @sec.triple Create SecTriple( this, RDF.li, o );
     * @throws UpdateDeniedException
     * @throws AddDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public SecuredContainer add(final long o)
            throws AddDeniedException, UpdateDeniedException, AuthenticationRequiredException {
        return add(asObject(o));
    }

    /**
     * @sec.graph Update
     * @sec.triple Create SecTriple( this, RDF.li, o );
     * @throws UpdateDeniedException
     * @throws AddDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public SecuredContainer add(final Object o)
            throws AddDeniedException, UpdateDeniedException, AuthenticationRequiredException {
        return add(asObject(o));
    }

    /**
     * @sec.graph Update
     * @sec.triple Create SecTriple( this, RDF.li, o );
     * @throws UpdateDeniedException
     * @throws AddDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public SecuredContainer add(final RDFNode o)
            throws AddDeniedException, UpdateDeniedException, AuthenticationRequiredException {
        checkUpdate();
        // ContainerImpl.add appends at rdf:_(size()+1); authorize that same slot.
        final int pos = holder.getBaseItem().size() + 1;
        checkAdd(pos, o.asNode());
        holder.getBaseItem().add(o);
        return holder.getSecuredItem();
    }

    /**
     * @sec.graph Update
     * @sec.triple Create SecTriple( this, RDF.li, o );
     * @throws UpdateDeniedException
     * @throws AddDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public SecuredContainer add(final String o)
            throws AddDeniedException, UpdateDeniedException, AuthenticationRequiredException {
        return add(o, "");
    }

    /**
     * @sec.graph Update
     * @sec.triple Create SecTriple( this, RDF.li, o );
     * @throws UpdateDeniedException
     * @throws AddDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public SecuredContainer add(final String o, final String l)
            throws AddDeniedException, UpdateDeniedException, AuthenticationRequiredException {
        return add(asLiteral(o, l));
    }

    /**
     * @sec.graph Update
     * @sec.triple Create SecTriple( this, RDF.li, o );
     * @throws UpdateDeniedException
     * @throws AddDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    protected void checkAdd(final int pos, final Literal literal)
            throws AddDeniedException, UpdateDeniedException, AuthenticationRequiredException {
        checkAdd(pos, literal.asNode());
    }

    protected void checkAdd(final int pos, final Node node)
            throws AddDeniedException, UpdateDeniedException, AuthenticationRequiredException {
        checkCreate(Triple.create(holder.getBaseItem().asNode(), RDF.li(pos).asNode(), node));
    }

    /**
     * @sec.graph Read
     * @sec.triple Read SecTriple( this, RDF.li, o );
     *
     *             if {@link SecurityEvaluator#isHardReadError()} is true and the
     *             user does not have read access then @{code false} is returned.
     *
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public boolean contains(final boolean o) throws ReadDeniedException, AuthenticationRequiredException {
        return contains(asObject(o));
    }

    /**
     * @sec.graph Read
     * @sec.triple Read SecTriple( this, RDF.li, o );
     *
     *             if {@link SecurityEvaluator#isHardReadError()} is true and the
     *             user does not have read access then @{code false} is returned.
     *
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public boolean contains(final char o) throws ReadDeniedException, AuthenticationRequiredException {
        return contains(asObject(o));
    }

    /**
     * @sec.graph Read
     * @sec.triple Read SecTriple( this, RDF.li, o );
     *
     *             if {@link SecurityEvaluator#isHardReadError()} is true and the
     *             user does not have read access then @{code false} is returned.
     *
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public boolean contains(final double o) throws ReadDeniedException, AuthenticationRequiredException {
        return contains(asObject(o));
    }

    /**
     * @sec.graph Read
     * @sec.triple Read SecTriple( this, RDF.li, o );
     *
     *             if {@link SecurityEvaluator#isHardReadError()} is true and the
     *             user does not have read access then @{code false} is returned.
     *
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public boolean contains(final float o) throws ReadDeniedException, AuthenticationRequiredException {
        return contains(asObject(o));
    }

    /**
     * @sec.graph Read
     * @sec.triple Read SecTriple( this, RDF.li, o );
     *
     *             if {@link SecurityEvaluator#isHardReadError()} is true and the
     *             user does not have read access then @{code false} is returned.
     *
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public boolean contains(final long o) throws ReadDeniedException, AuthenticationRequiredException {
        return contains(asObject(o));
    }

    /**
     * @sec.graph Read
     * @sec.triple Read SecTriple( this, RDF.li, o );
     *
     *             if {@link SecurityEvaluator#isHardReadError()} is true and the
     *             user does not have read access then @{code false} is returned.
     *
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public boolean contains(final Object o) throws ReadDeniedException, AuthenticationRequiredException {
        return contains(asObject(o));
    }

    /**
     * @sec.graph Read
     * @sec.triple Read SecTriple( this, RDF.li, o );
     *
     *             if {@link SecurityEvaluator#isHardReadError()} is true and the
     *             user does not have read access then @{code false} is returned.
     *
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public boolean contains(final RDFNode o) throws ReadDeniedException, AuthenticationRequiredException {
        // iterator checks reads
        final SecuredNodeIterator<RDFNode> iter = iterator();
        try {
            while (iter.hasNext()) {
                if (iter.next().asNode().equals(o.asNode())) {
                    return true;
                }
            }
            return false;
        } finally {
            iter.close();
        }
    }

    /**
     * @sec.graph Read
     * @sec.triple Read SecTriple( this, RDF.li, o );
     *
     *             if {@link SecurityEvaluator#isHardReadError()} is true and the
     *             user does not have read access then @{code false} is returned.
     *
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public boolean contains(final String o) throws ReadDeniedException, AuthenticationRequiredException {
        return contains(o, "");
    }

    /**
     * @sec.graph Read
     * @sec.triple Read SecTriple( this, RDF.li, o );
     *
     *             if {@link SecurityEvaluator#isHardReadError()} is true and the
     *             user does not have read access then @{code false} is returned.
     *
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public boolean contains(final String o, final String l)
            throws ReadDeniedException, AuthenticationRequiredException {
        return contains(asLiteral(o, l));
    }

    protected int getAddIndex() {
        int pos = -1;
        final ExtendedIterator<Statement> iter = holder.getBaseItem().listProperties();
        try {
            while (iter.hasNext()) {
                pos = Math.max(pos, getIndex(iter.next().getPredicate()));
            }
        } finally {
            iter.close();
        }
        return pos + 1;
    }

    protected static int getIndex(final Property p) {
        if (p.getNameSpace().equals(RDF.getURI()) && p.getLocalName().startsWith("_")) {
            try {
                return Integer.parseInt(p.getLocalName().substring(1));
            } catch (final NumberFormatException e) {
                // acceptable;
            }
        }
        return -1;
    }

    /**
     * An iterator of statements that have predicates that start with '_' followed
     * by a number and for which the user has the specified permission.
     *
     * @param perm the permission to check
     * @return an ExtendedIterator of statements.
     */
    protected ExtendedIterator<Statement> getStatementIterator(final Action perm) {
        return holder.getBaseItem().listProperties().filterKeep(new ContainerFilter())
                .filterKeep(new PermStatementFilter(perm, this));
    }

    /**
     * An iterator of statements that have predicates that start with '_' followed
     * by a number and for which the user has the specified permissions.
     *
     * @param perm the permissions to check
     * @return an ExtendedIterator of statements.
     */
    protected ExtendedIterator<Statement> getStatementIterator(final Set<Action> perm) {
        return holder.getBaseItem().listProperties().filterKeep(new ContainerFilter())
                .filterKeep(new PermStatementFilter(perm, this));
    }

    @Override
    public boolean isAlt() {
        return holder.getBaseItem().isAlt();
    }

    @Override
    public boolean isBag() {
        return holder.getBaseItem().isBag();
    }

    @Override
    public boolean isSeq() {
        return holder.getBaseItem().isSeq();
    }

    /**
     * @sec.graph Read
     * @sec.triple Read on each triple ( this, rdf:li_? node ) returned by iterator;
     *
     *             if {@link SecurityEvaluator#isHardReadError()} is true and the
     *             user does not have read access then an empty iterator is
     *             returned.
     *
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public SecuredNodeIterator<RDFNode> iterator() {
        // listProperties calls checkRead();
        SecuredStatementIterator iter = listProperties();
        try {
            // List<Statement> ls = iter.toList();
            SortedSet<Statement> result = new TreeSet<>(new ContainerComparator());
            while (iter.hasNext()) {
                Statement stmt = iter.next();
                if (stmt.getPredicate().getOrdinal() > 0) {
                    result.add(stmt);
                }
            }
            return new SecuredNodeIterator<>(getModel(),
                    new StatementRemovingIterator(result.iterator()).mapWith(s -> s.getObject()));
        } finally {
            iter.close();
        }
    }

    /**
     * @param perms the Permissions required on each node returned
     * @sec.graph Read
     * @sec.triple Read + perms on each triple ( this, rdf:li_? node ) returned by
     *             iterator;
     *
     *             if {@link SecurityEvaluator#isHardReadError()} is true and the
     *             user does not have read access then an empty iterator is
     *             returned.
     *
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    protected SecuredNodeIterator<RDFNode> iterator(final Set<Action> perms) {
        checkRead();
        final Set<Action> permsCopy = new HashSet<>(perms);
        permsCopy.add(Action.Read);
        final ExtendedIterator<RDFNode> ni = getStatementIterator(permsCopy).mapWith(o -> o.getObject());
        return new SecuredNodeIterator<>(getModel(), ni);

    }

    /**
     * @sec.graph Update
     * @sec.triple Delete s as triple;
     * @throws UpdateDeniedException
     * @throws DeleteDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public SecuredContainer remove(final Statement s)
            throws UpdateDeniedException, DeleteDeniedException, AddDeniedException, AuthenticationRequiredException {
        checkUpdate();
        // remove(Statement) is not a simple delete: to keep the container
        // gap-free the base implementation rewrites membership triples beyond
        // s. Bag/Alt (ContainerImpl.remove) move the last member into s's slot
        // -- deleting the last triple and creating one in s's slot -- and Seq
        // (SeqImpl.remove) shifts every following member down. Every such
        // triple must be authorized, not just s, or a caller with narrow
        // permissions could delete/renumber members it does not control.
        if (canDelete(Triple.ANY) && canCreate(Triple.ANY)) {
            checkDelete(s.asTriple());
        } else {
            checkWriteDelta(c -> c.remove(c.getModel().asStatement(s.asTriple())));
        }
        holder.getBaseItem().remove(s);
        return holder.getSecuredItem();
    }

    /**
     * Authorize every triple that a container mutation will delete or create.
     * <p>
     * Jena's container mutators are not simple single-triple edits: to keep the
     * container gap-free they rewrite membership triples beyond the one named
     * (Bag/Alt {@code remove} moves the last member into the vacated slot; Seq
     * {@code add}/{@code remove} shift the trailing members up/down). Rather
     * than duplicate each strategy (which could drift from Jena), the mutation
     * is replayed on an isolated copy of the container and the resulting delta
     * is checked: removed triples require {@code Delete}, added triples require
     * {@code Create}. Correct for Bag/Alt (swap-with-last) and Seq (shift) alike.
     *
     * @param mutation the container operation to authorize, applied to a private
     *                 copy of this container (never to the live base).
     * @throws DeleteDeniedException           if a deleted triple is not permitted.
     * @throws AddDeniedException              if a created triple is not permitted.
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    protected void checkWriteDelta(final Consumer<Container> mutation)
            throws DeleteDeniedException, AddDeniedException, AuthenticationRequiredException {
        final Container base = holder.getBaseItem();
        final Node subject = base.asNode();

        // Copy the container's outgoing statements into a scratch model, at the
        // same node, so the replayed mutation produces triples with the real
        // subject and objects.
        final Model scratch = ModelFactory.createDefaultModel();
        final Graph scratchGraph = scratch.getGraph();
        final Set<Triple> before = new HashSet<>();
        final ExtendedIterator<Triple> iter = base.getModel().getGraph().find(subject, Node.ANY, Node.ANY);
        try {
            while (iter.hasNext()) {
                final Triple t = iter.next();
                before.add(t);
                scratchGraph.add(t);
            }
        } finally {
            iter.close();
        }

        // Replay with a same-typed container view so the same renumbering runs.
        final Resource scratchSubject = scratch.getRDFNode(subject).asResource();
        final Container scratchContainer = base instanceof Seq ? scratchSubject.as(Seq.class)
                : base instanceof Alt ? scratchSubject.as(Alt.class) : scratchSubject.as(Bag.class);
        mutation.accept(scratchContainer);

        final Set<Triple> after = new HashSet<>();
        final ExtendedIterator<Triple> iter2 = scratchGraph.find(subject, Node.ANY, Node.ANY);
        try {
            while (iter2.hasNext()) {
                after.add(iter2.next());
            }
        } finally {
            iter2.close();
        }

        for (final Triple t : before) {
            if (!after.contains(t)) {
                checkDelete(t);
            }
        }
        for (final Triple t : after) {
            if (!before.contains(t)) {
                checkCreate(t);
            }
        }
    }

    /**
     * @sec.graph Read
     * @throws ReadDeniedException
     * @throws AuthenticationRequiredException if user is not authenticated and is
     *                                         required to be.
     */
    @Override
    public int size() throws ReadDeniedException, AuthenticationRequiredException {
        checkRead();
        if (canRead(Triple.ANY)) {
            return holder.getBaseItem().size();
        }
        // Count only the members the caller can read — consistent with iterator().
        final SecuredNodeIterator<RDFNode> iter = iterator();
        int i = 0;
        try {
            while (iter.hasNext()) {
                i++;
                iter.next();
            }
        } finally {
            iter.close();
        }
        return i;
    }

    static class ContainerComparator implements Comparator<Statement> {

        @Override
        public int compare(Statement arg0, Statement arg1) {
            return Integer.valueOf(arg0.getPredicate().getOrdinal()).compareTo(arg1.getPredicate().getOrdinal());
        }

    }

    static class StatementRemovingIterator extends WrappedIterator<Statement> {
        private Statement stmt;

        public StatementRemovingIterator(Iterator<? extends Statement> base) {
            super(base);
        }

        @Override
        public Statement next() {
            stmt = super.next();
            return stmt;
        }

        @Override
        public void remove() {
            stmt.remove();
            super.remove();
        }
    }
}
