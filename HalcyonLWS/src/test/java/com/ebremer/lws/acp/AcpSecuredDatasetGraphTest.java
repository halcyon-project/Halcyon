package com.ebremer.lws.acp;

import com.ebremer.lws.vocab.LWSX;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link AcpSecuredDatasetGraph}, the dataset the {@code /rdf2} SPARQL
 * endpoint queries. Two guarantees carry the endpoint's security story: the
 * internal graphs (system, ACP, subscriptions, keys, sharing) are invisible
 * even to an evaluator that allows EVERYTHING — a private key lives in one of
 * them — and a resource the agent may not read is absent from every access
 * path (enumeration, wildcard find, union, SPARQL), not merely filtered from
 * some of them.
 */
class AcpSecuredDatasetGraphTest {

    private static final String R1 = "https://host/W3Clws/readable";
    private static final String R2 = "https://host/W3Clws/forbidden";

    /** Allows Read on the given graph URIs; the principal is inert. */
    private static SecurityEvaluator allowing(Set<String> readable) {
        return new SecurityEvaluator() {
            @Override
            public boolean evaluate(Object principal, Action action, Node graphIRI) {
                return action == Action.Read && graphIRI != null && graphIRI.isURI()
                        && readable.contains(graphIRI.getURI());
            }

            @Override
            public boolean evaluate(Object principal, Action action, Node graphIRI, Triple t) {
                return evaluate(principal, action, graphIRI);
            }

            @Override
            public Object getPrincipal() {
                return "test-agent";
            }

            @Override
            public boolean isPrincipalAuthenticated(Object principal) {
                return true;
            }

            @Override
            public boolean isHardReadError() {
                return false;
            }
        };
    }

    /** The raw store: two resources plus every internal graph, all populated. */
    private static DatasetGraph rawStore() {
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        dsg.executeWrite(() -> {
            for (String g : List.of(R1, R2, LWSX.SYSTEM_GRAPH, LWSX.ACP_GRAPH,
                    LWSX.SUBSCRIPTION_GRAPH, LWSX.KEYS_GRAPH, LWSX.SHARING_GRAPH)) {
                Node gn = NodeFactory.createURI(g);
                dsg.add(gn, gn, NodeFactory.createURI("urn:test:p"),
                        NodeFactory.createLiteralString("secret-or-not"));
            }
        });
        return dsg;
    }

    private static Set<String> graphUris(Iterator<Node> it) {
        Set<String> out = new HashSet<>();
        it.forEachRemaining(n -> out.add(n.getURI()));
        return out;
    }

    @Test
    void internalGraphsAreInvisibleEvenToAnAllowEverythingEvaluator() {
        Set<String> everything = Set.of(R1, R2, LWSX.SYSTEM_GRAPH, LWSX.ACP_GRAPH,
                LWSX.SUBSCRIPTION_GRAPH, LWSX.KEYS_GRAPH, LWSX.SHARING_GRAPH);
        DatasetGraph raw = rawStore();
        AcpSecuredDatasetGraph view =
                new AcpSecuredDatasetGraph(raw, allowing(everything));

        raw.executeRead(() -> {
            Set<String> listed = graphUris(view.listGraphNodes());
            assertTrue(listed.contains(R1));
            assertTrue(listed.contains(R2));
            for (String hidden : List.of(LWSX.SYSTEM_GRAPH, LWSX.ACP_GRAPH,
                    LWSX.SUBSCRIPTION_GRAPH, LWSX.KEYS_GRAPH, LWSX.SHARING_GRAPH)) {
                assertFalse(listed.contains(hidden), hidden + " must never be listed");
                assertFalse(view.containsGraph(NodeFactory.createURI(hidden)));
                assertTrue(view.getGraph(NodeFactory.createURI(hidden)).isEmpty(),
                        hidden + " must read as empty");
            }
            // Wildcard scans must not leak them either.
            for (var it = view.find(Node.ANY, Node.ANY, Node.ANY, Node.ANY); it.hasNext();) {
                Quad q = it.next();
                assertFalse(LWSX.KEYS_GRAPH.equals(q.getGraph().getURI()),
                        "a wildcard find must never surface the keys graph");
            }
        });
    }

    @Test
    void unreadableResourcesAreAbsentFromEveryAccessPath() {
        DatasetGraph raw = rawStore();
        AcpSecuredDatasetGraph view = new AcpSecuredDatasetGraph(raw, allowing(Set.of(R1)));

        raw.executeRead(() -> {
            Set<String> listed = graphUris(view.listGraphNodes());
            assertTrue(listed.contains(R1));
            assertFalse(listed.contains(R2), "an unreadable resource must not be enumerable");

            Set<String> found = new HashSet<>();
            view.find(Node.ANY, Node.ANY, Node.ANY, Node.ANY)
                    .forEachRemaining(q -> found.add(q.getGraph().getURI()));
            assertTrue(found.contains(R1));
            assertFalse(found.contains(R2), "an unreadable resource must not match wildcards");

            assertTrue(view.getGraph(NodeFactory.createURI(R2)).isEmpty());
            assertFalse(view.getUnionGraph().contains(
                    NodeFactory.createURI(R2), Node.ANY, Node.ANY),
                    "the union must be built over the filtered view");
            assertTrue(view.getDefaultGraph().isEmpty(),
                    "nothing lives in the default graph");
        });
    }

    @Test
    void sparqlOverTheViewSeesOnlyReadableGraphs() {
        DatasetGraph raw = rawStore();
        AcpSecuredDatasetGraph view = new AcpSecuredDatasetGraph(raw, allowing(Set.of(R1)));
        Dataset ds = DatasetFactory.wrap(view);

        raw.executeRead(() -> {
            Set<String> graphs = new HashSet<>();
            try (QueryExecution qe = QueryExecutionFactory.create(
                    "select distinct ?g where { graph ?g { ?s ?p ?o } }", ds)) {
                ResultSet rs = qe.execSelect();
                while (rs.hasNext()) {
                    graphs.add(rs.next().getResource("g").getURI());
                }
            }
            assertTrue(graphs.contains(R1));
            assertFalse(graphs.contains(R2), "SPARQL must not discover unreadable resources");
            assertFalse(graphs.contains(LWSX.KEYS_GRAPH), "SPARQL must never see the keys graph");
        });
    }
}
