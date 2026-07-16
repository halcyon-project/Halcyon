package com.ebremer.lws.search;

import com.ebremer.lws.acp.AccessMode;
import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.json.LwsJson;
import com.ebremer.lws.store.LinksetStore;
import com.ebremer.lws.store.LwsResource;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.store.ResourceRegistry;
import com.ebremer.lws.vocab.LWSX;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.vocabulary.RDF;

/**
 * The Type Index and Type Search services.
 *
 * <p>The spec's demand is uncompromising — unauthorized results "MUST be omitted entirely", and a
 * client "must not be able to discover the existence of a specific resource instance, or that a
 * specific type exists in the storage at all". It also, in the same breath, permits a <em>derived
 * index</em> to make this affordable, "provided current authorization is applied as a filter over
 * it". That is exactly the shape here.
 *
 * <p><strong>The index is TDB2's own.</strong> Candidates are enumerated with a quad pattern —
 * {@code find(ANY, ANY, rdf:type, T)} — which TDB2 answers from its predicate/object index, so a
 * type search touches only the resources bearing that type rather than scanning every named graph
 * in the storage. Authorization is then applied as a filter over that candidate set, resource by
 * resource, against the agent's <em>current</em> access. Nothing is precomputed and nothing is
 * cached across requests: the spec allows type derivation to be eventually consistent but explicitly
 * refuses that latitude to authorization, so the ACP check is always live.
 *
 * <p>The authorization decision is the same one the secured dataset used to make — a resource's
 * named graph <em>is</em> its URI, and {@code AcpEngine.allows(agent, uri, Read)} is precisely what
 * {@code AcpSecurityEvaluator} consulted — so enumerating raw and filtering explicitly yields an
 * identical result set, only without an ACP evaluation on every graph in the store.
 */
public final class SearchService {

    /** Members per page. */
    public static final int PAGE_SIZE = 100;

    private static final Node RDF_TYPE = RDF.type.asNode();

    private final LwsStore store;
    private final LwsStorageConfig cfg;
    private final AgentContext agent;
    private final AcpEngine acp;

    public SearchService(LwsStore store, LwsStorageConfig cfg, AgentContext agent, AcpEngine acp) {
        this.store = store;
        this.cfg = cfg;
        this.agent = agent;
        this.acp = acp;
    }

    // --- Type Index ---------------------------------------------------------

    /** Every distinct type in the storage that this agent is allowed to know exists. */
    public List<String> types() {
        TreeSet<String> visible = new TreeSet<>();
        store.read(() -> {
            DatasetGraph dsg = store.raw().asDatasetGraph();

            // The distinct types borne by this storage's resources, from the quad index rather than
            // a walk of every graph. Cheap: it iterates only the rdf:type quads.
            Set<String> present = new HashSet<>();
            for (var it = dsg.find(Node.ANY, Node.ANY, RDF_TYPE, Node.ANY); it.hasNext();) {
                Quad q = it.next();
                if (q.getObject().isURI() && belongsHere(q.getGraph())) {
                    present.add(q.getObject().getURI());
                }
            }
            // A type is visible to this agent iff it can read at least one resource bearing it —
            // otherwise disclosing the type would disclose that such a resource exists.
            for (String t : present) {
                if (anyReadableOfType(dsg, t)) {
                    visible.add(t);
                }
            }
        });
        return new ArrayList<>(visible);
    }

    /** Whether the agent can read any resource of {@code type}. Short-circuits on the first. */
    private boolean anyReadableOfType(DatasetGraph dsg, String type) {
        for (var it = dsg.find(Node.ANY, Node.ANY, RDF_TYPE, NodeFactory.createURI(type));
                it.hasNext();) {
            Quad q = it.next();
            if (belongsHere(q.getGraph()) && q.getSubject().isURI()
                    && acp.allows(agent, q.getSubject().getURI(), AccessMode.READ)) {
                return true;
            }
        }
        return false;
    }

    // --- Type Search --------------------------------------------------------

    /** A page of matching resources. */
    public record Page(List<LwsJson.Item> items, long total, long lastScannedSeq, boolean more) {
    }

    /**
     * Resources matching a conjunctive-normal-form filter.
     *
     * <p>A {@code type} filter always denotes the type the matched resource itself bears; it never
     * denotes the types of a container's members. The two native LWS classes are ordinary values,
     * so filtering on {@code lws:Container} selects containers, and combining {@code schema:Person}
     * with {@code lws:DataResource} selects data resources that are also people.
     */
    public Page search(LwsQuery q, Cursor cursor) {
        List<LwsResource> matched = new ArrayList<>();
        long[] highWater = {cursor.afterSeq()};
        boolean[] more = {false};
        long[] total = {0};

        store.read(() -> {
            DatasetGraph dsg = store.raw().asDatasetGraph();
            Model sys = store.system();
            ResourceRegistry reg = new ResourceRegistry(store, cfg);

            record Cand(String uri, long seq) {
            }
            List<Cand> candidates = new ArrayList<>();
            for (String uri : candidateUris(dsg, sys, q)) {
                // Authorization applied as a filter over the derived candidate set. A resource the
                // agent may not read is dropped here, exactly as the secured dataset once hid it.
                if (acp.allows(agent, uri, AccessMode.READ)) {
                    candidates.add(new Cand(uri, seqOf(sys, uri)));
                }
            }
            candidates.sort(Comparator.comparingLong(Cand::seq).thenComparing(Cand::uri));

            for (Cand c : candidates) {
                if (c.seq() <= cursor.afterSeq()) {
                    continue;
                }
                Model m = store.raw().getNamedModel(c.uri());
                if (!matches(q, c.uri(), m)) {
                    // Still scanned: the cursor must advance past it, or the next page would
                    // re-examine it forever.
                    highWater[0] = Math.max(highWater[0], c.seq());
                    continue;
                }
                total[0]++;
                if (matched.size() < PAGE_SIZE) {
                    reg.find(c.uri()).ifPresent(matched::add);
                    highWater[0] = Math.max(highWater[0], c.seq());
                } else {
                    more[0] = true;
                }
            }
        });

        List<LwsJson.Item> items = new ArrayList<>();
        for (LwsResource r : matched) {
            items.add(r.asItem());
        }
        return new Page(items, total[0], highWater[0], more[0]);
    }

    /**
     * The candidate resource URIs for a query, narrowed by a type constraint when there is one.
     *
     * <p>A CNF query's {@code type} constraint is a set of AND-ed OR-groups. Narrowing on the first
     * OR-group alone yields a <em>superset</em> of the answer — {@link #matches} then verifies the
     * whole filter — so the candidate set stays correct while touching only resources of the named
     * types. With no type constraint (an empty filter, or one over link relations only) there is
     * nothing to narrow on and every resource is a candidate, which is inherent: listing everything
     * is O(n) whatever index exists.
     */
    private Set<String> candidateUris(DatasetGraph dsg, Model sys, LwsQuery q) {
        List<List<String>> typeGroups = q.constraints().get(LwsQuery.TYPE);
        if (typeGroups != null && !typeGroups.isEmpty()) {
            Set<String> out = new HashSet<>();
            for (String type : typeGroups.get(0)) {
                for (var it = dsg.find(Node.ANY, Node.ANY, RDF_TYPE, NodeFactory.createURI(type));
                        it.hasNext();) {
                    Quad quad = it.next();
                    if (belongsHere(quad.getGraph()) && quad.getSubject().isURI()) {
                        out.add(quad.getSubject().getURI());
                    }
                }
            }
            return out;
        }
        Set<String> out = new HashSet<>();
        for (var it = sys.listSubjectsWithProperty(LWSX.seq); it.hasNext();) {
            Resource s = it.next();
            if (s.isURIResource() && belongsHere(s.getURI())) {
                out.add(s.getURI());
            }
        }
        return out;
    }

    private long seqOf(Model sys, String uri) {
        var st = sys.getProperty(ResourceFactory.createResource(uri), LWSX.seq);
        if (st == null || !st.getObject().isLiteral()) {
            return 0;
        }
        try {
            return st.getLong();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Does this resource satisfy every AND-group of every constraint? */
    private boolean matches(LwsQuery q, String uri, Model m) {
        if (q.isEmpty()) {
            return true;
        }
        var subject = ResourceFactory.createResource(uri);

        for (var e : q.constraints().entrySet()) {
            String key = e.getKey();

            Set<String> declared = new LinkedHashSet<>();
            if (LwsQuery.TYPE.equals(key)) {
                for (RDFNode t : m.listObjectsOfProperty(subject, RDF.type).toList()) {
                    if (t.isURIResource()) {
                        declared.add(t.asResource().getURI());
                    }
                }
            } else {
                // A descriptive link relation. An unindexed relation and a value that matches
                // nothing both yield no results here — and the spec requires the two to be
                // indistinguishable, so no error is raised either way.
                Model links = store.raw().getNamedModel(LinksetStore.graphUri(uri));
                var p = LinksetStore.predicateFor(key);
                for (RDFNode t : links.listObjectsOfProperty(subject, p).toList()) {
                    if (t.isURIResource()) {
                        declared.add(t.asResource().getURI());
                    }
                }
            }

            for (List<String> orGroup : e.getValue()) {
                boolean any = orGroup.stream().anyMatch(declared::contains);
                if (!any) {
                    return false;
                }
            }
        }
        return true;
    }

    /** True for a resource graph of this storage; false for the other storage and internal graphs. */
    private boolean belongsHere(Node graph) {
        return graph.isURI() && belongsHere(graph.getURI());
    }

    private boolean belongsHere(String graphUri) {
        return graphUri.startsWith(cfg.baseUri() + "/")
                && !graphUri.endsWith(LwsStorageConfig.LINKSET_SUFFIX)
                && !graphUri.endsWith(LwsStorageConfig.ACR_SUFFIX);
    }
}
