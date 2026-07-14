package com.ebremer.lws.acp;

import com.ebremer.lws.vocab.LWSX;
import java.util.Iterator;
import java.util.Set;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphReadOnly;
import org.apache.jena.sparql.core.GraphView;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.graph.GraphZero;
import org.apache.jena.util.iterator.WrappedIterator;

/**
 * A read-only, ACP-filtered view of the store, built on Halcyon's
 * {@code jena-permissions} fork.
 *
 * <p>This is what lets the Type Index and Type Search services be correct by
 * construction. Their spec does not merely ask that unauthorized results be omitted —
 * it says a client "must not be able to discover the existence of a specific resource
 * instance, or that a specific type exists in the storage at all". Filtering a result
 * set after the fact satisfies that only as long as every future call site remembers
 * to. Filtering the <em>dataset</em> means an unauthorized resource simply is not
 * there to be found, and a query cannot accidentally see it.
 *
 * <p>Four graphs are hidden unconditionally, from graph enumeration and from every
 * wildcard scan alike: {@code urn:lws:system}, which holds the mapping from resource
 * URI to storage key; {@code urn:lws:acp}, which holds the access control policies
 * themselves; {@code urn:lws:subscriptions}, which holds subscribers' inboxes and topics;
 * and {@code urn:lws:keys}, which holds the webhook signing key and the cursor HMAC secret.
 * None is an LWS resource, and a search that could reach any of them would hand a client the
 * storage's internal layout, the rules that are supposed to be constraining it, or its
 * private key.
 *
 * <p>Read-only on purpose. Writes go through the raw dataset after an explicit
 * {@link AcpEngine} check at the HTTP layer, because the graph-level evaluator cannot
 * express LWS's write rules — see {@link AcpSecurityEvaluator}.
 */
public final class AcpSecuredDatasetGraph extends DatasetGraphReadOnly {

    private static final Set<String> HIDDEN = Set.of(
            LWSX.SYSTEM_GRAPH, LWSX.ACP_GRAPH, LWSX.SUBSCRIPTION_GRAPH, LWSX.KEYS_GRAPH,
            LWSX.SHARING_GRAPH);

    private final DatasetGraph base;
    private final SecurityEvaluator evaluator;
    private final Object principal;

    public AcpSecuredDatasetGraph(DatasetGraph base, SecurityEvaluator evaluator) {
        super(base);
        this.base = base;
        this.evaluator = evaluator;
        this.principal = evaluator.getPrincipal();
    }

    private boolean visible(Node g) {
        if (g == null || !g.isURI()) {
            return false;
        }
        if (HIDDEN.contains(g.getURI())) {
            return false;
        }
        return evaluator.evaluate(principal, SecurityEvaluator.Action.Read, g);
    }

    @Override
    public Graph getDefaultGraph() {
        // Nothing lives in the default graph; every resource is its own named graph.
        return GraphZero.instance();
    }

    @Override
    public Graph getGraph(Node graphNode) {
        if (!visible(graphNode)) {
            return GraphZero.instance();
        }
        // Wrapped even though the graph as a whole is already permitted: the fork's
        // proxy is what enforces the decision on every triple that leaves it.
        return Factory.getInstance(evaluator, graphNode.getURI(), base.getGraph(graphNode));
    }

    /**
     * The union is built over {@code this}, never over {@code base}. Building it over the
     * unfiltered dataset would hand a query every triple in the store through the back
     * door, ACP and hidden graphs alike.
     */
    @Override
    public Graph getUnionGraph() {
        return GraphView.createUnionGraph(this);
    }

    @Override
    public boolean containsGraph(Node graphNode) {
        return visible(graphNode) && base.containsGraph(graphNode);
    }

    /**
     * Only the graphs this agent may read.
     *
     * <p>The enumeration itself is the disclosure: a resource's graph URI <em>is</em> its
     * URI, so listing a graph the agent cannot read would reveal that the resource
     * exists — precisely what the search spec forbids.
     */
    @Override
    public Iterator<Node> listGraphNodes() {
        return WrappedIterator.create(base.listGraphNodes()).filterKeep(this::visible);
    }

    @Override
    public Iterator<Quad> find() {
        return find(Node.ANY, Node.ANY, Node.ANY, Node.ANY);
    }

    @Override
    public Iterator<Quad> find(Quad quad) {
        return find(quad.getGraph(), quad.getSubject(), quad.getPredicate(), quad.getObject());
    }

    @Override
    public Iterator<Quad> find(Node g, Node s, Node p, Node o) {
        return WrappedIterator.create(base.find(g, s, p, o))
                .filterKeep(q -> visible(q.getGraph()));
    }

    @Override
    public Iterator<Quad> findNG(Node g, Node s, Node p, Node o) {
        return WrappedIterator.create(base.findNG(g, s, p, o))
                .filterKeep(q -> visible(q.getGraph()));
    }

    @Override
    public boolean contains(Node g, Node s, Node p, Node o) {
        return find(g, s, p, o).hasNext();
    }

    @Override
    public boolean contains(Quad quad) {
        return contains(quad.getGraph(), quad.getSubject(), quad.getPredicate(), quad.getObject());
    }

    @Override
    public boolean isEmpty() {
        return !find().hasNext();
    }
}
