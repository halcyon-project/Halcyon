package com.ebremer.halcyon.server;

import com.ebremer.beakgraph.core.BeakGraph;
import com.ebremer.beakgraph.pool.BeakGraphPool;
import com.ebremer.lws.acp.AccessMode;
import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.store.LwsResource;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.store.ResourceRegistry;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SPARQL over ONE BeakGraph (HDF5) resource's own dataset — the shared engine behind both the
 * {@code /rdf2?iri=} endpoint ({@link LwsSparqlServlet}) and the resource-URL query filter
 * ({@link LwsResourceSparqlFilter}), so every {@code .h5} in a storage is a SPARQL endpoint at its
 * own URL <em>and</em> at {@code /rdf2?iri=}. Registry-resolved (never a client path), ACP-gated
 * (the caller's own {@code Read}; a refusal is 404, so an unauthorized resource is not discoverable),
 * pool-served, and bounded by a timeout — the C2-deletion conditions the per-BeakGraph endpoint
 * exists under.
 */
public final class BeakGraphEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(BeakGraphEndpoint.class);
    private static final long TIMEOUT_SECONDS = 60;

    /** What {@link #serve} did: it answered (SERVED), or the target is not a queryable BeakGraph. */
    public enum Outcome { SERVED, NOT_FOUND, NOT_BEAKGRAPH }

    private BeakGraphEndpoint() {
    }

    /** True if {@code iri} is a data resource the agent may Read and is a BeakGraph (HDF5). */
    public static boolean isReadableBeakGraph(String iri, AgentContext agent) {
        LwsStorageConfig cfg = storageOf(iri);
        if (cfg == null) {
            return false;
        }
        LwsResource r = resolveReadable(iri, agent, cfg);
        return r != null && isBeakGraph(r);
    }

    /**
     * Resolve {@code iri}, authorize the agent's Read, and run {@code queryString} over the
     * resource's BeakGraph. On success it writes the results (or a 400/502) and returns SERVED.
     * It returns NOT_FOUND (missing/unreadable — no discovery) or NOT_BEAKGRAPH (readable but not
     * HDF5) <em>without writing anything</em>, so the caller decides (404/400, or pass through).
     */
    public static Outcome serve(HttpServletResponse response, String queryString, String iri,
            AgentContext agent, String accept) throws IOException {
        LwsStorageConfig cfg = storageOf(iri);
        if (cfg == null) {
            return Outcome.NOT_FOUND;
        }
        LwsStore store = LwsStore.get();
        LwsResource r = resolveReadable(iri, agent, cfg);
        if (r == null) {
            return Outcome.NOT_FOUND;
        }
        if (!isBeakGraph(r)) {
            return Outcome.NOT_BEAKGRAPH;
        }
        Query query;
        try {
            query = QueryFactory.create(queryString);
        } catch (QueryParseException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "query parse error: " + ex.getMessage());
            return Outcome.SERVED;
        }
        Path blob = store.contentStore(cfg).pathFor(r.storageKey(), r.ext());
        java.net.URI key = blob.toUri();
        BeakGraph bg = null;
        try {
            bg = BeakGraphPool.getPool().borrowObject(key);
            try (QueryExecution qe = QueryExecution.dataset(bg.getDataset()).query(query)
                    .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()) {
                respond(response, qe, accept);
            }
        } catch (IOException ex) {
            // The client went away mid-stream; nothing to answer.
        } catch (Exception ex) {
            LOG.warn("BeakGraph query of {} failed", iri, ex);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY,
                        "the resource could not be opened as a BeakGraph");
            }
        } finally {
            if (bg != null) {
                try {
                    BeakGraphPool.getPool().returnObject(key, bg);
                } catch (Exception ex) {
                    LOG.warn("BeakGraph pool return failed for {}", iri, ex);
                }
            }
        }
        return Outcome.SERVED;
    }

    /** Resolve+authorize in one short read transaction; null if missing, a container, or unreadable. */
    private static LwsResource resolveReadable(String iri, AgentContext agent, LwsStorageConfig cfg) {
        LwsStore store = LwsStore.get();
        LwsResource r = store.read(() ->
                new AcpEngine(store).allows(agent, iri, AccessMode.READ)
                        ? new ResourceRegistry(store, cfg).find(iri).orElse(null)
                        : null);
        return (r == null || r.isContainer() || r.storageKey() == null) ? null : r;
    }

    private static boolean isBeakGraph(LwsResource r) {
        return "application/x-hdf5".equalsIgnoreCase(r.mediaType())
                || "application/x-hdf".equalsIgnoreCase(r.mediaType())
                || ".h5".equalsIgnoreCase(r.ext());
    }

    /** The configured storage a URI belongs to, or {@code null} (same rule as SaveStackServlet). */
    static LwsStorageConfig storageOf(String uri) {
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            if (uri.startsWith(cfg.baseUri() + "/")) {
                return cfg;
            }
        }
        return null;
    }

    /** Serialize by query type, negotiating the common formats plainly. */
    public static void respond(HttpServletResponse response, QueryExecution qe, String accept)
            throws IOException {
        String a = accept == null ? "" : accept.toLowerCase();
        OutputStream out = response.getOutputStream();
        Query q = qe.getQuery();
        if (q.isSelectType()) {
            ResultSet rs = qe.execSelect();
            if (a.contains("xml")) {
                response.setContentType("application/sparql-results+xml");
                ResultSetFormatter.outputAsXML(out, rs);
            } else if (a.contains("csv")) {
                response.setContentType("text/csv");
                ResultSetFormatter.outputAsCSV(out, rs);
            } else if (a.contains("tab-separated")) {
                response.setContentType("text/tab-separated-values");
                ResultSetFormatter.outputAsTSV(out, rs);
            } else {
                response.setContentType("application/sparql-results+json");
                ResultSetFormatter.outputAsJSON(out, rs);
            }
            return;
        }
        if (q.isAskType()) {
            boolean b = qe.execAsk();
            if (a.contains("xml")) {
                response.setContentType("application/sparql-results+xml");
                ResultSetFormatter.outputAsXML(out, b);
            } else {
                response.setContentType("application/sparql-results+json");
                ResultSetFormatter.outputAsJSON(out, b);
            }
            return;
        }
        Model m = q.isConstructType() ? qe.execConstruct() : qe.execDescribe();
        if (a.contains("n-triples")) {
            response.setContentType("application/n-triples");
            RDFDataMgr.write(out, m, Lang.NTRIPLES);
        } else {
            response.setContentType("text/turtle");
            RDFDataMgr.write(out, m, Lang.TURTLE);
        }
    }
}
