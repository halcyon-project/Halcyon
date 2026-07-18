package com.ebremer.halcyon.server;

import com.ebremer.beakgraph.core.BeakGraph;
import com.ebremer.beakgraph.pool.BeakGraphPool;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.lws.acp.AccessMode;
import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.acp.AcpSecuredDatasetGraph;
import com.ebremer.lws.acp.AcpSecurityEvaluator;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.store.LwsResource;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.store.ResourceRegistry;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code /rdf2} — read-only SPARQL over the W3C LWS module's OWN TDB2
 * ({@code :LWSStoreLocation}), the query surface {@code /rdf} is for the
 * classic store.
 *
 * <p><strong>Never the raw dataset.</strong> Every request builds the
 * ACP-secured view ({@link AcpSecuredDatasetGraph}) for the CALLER — the
 * signed-in session's WebID, or the public agent — so a query answers exactly
 * what that agent's own LWS {@code GET}s could fetch: the internal graphs
 * (system, ACP, subscriptions, keys, sharing) are unconditionally invisible,
 * and a resource the agent may not read is not discoverable at all (the Type
 * Search guarantee, reused). The evaluator is built fresh per request, per its
 * own one-instance-per-request contract; the ACP decision is always live.
 *
 * <p><strong>Read-only by construction, twice over</strong> (H1 discipline):
 * the request is parsed as a SPARQL <em>Query</em> — an update does not parse
 * as one and is answered 400 — and the dataset itself is a
 * {@code DatasetGraphReadOnly}.
 *
 * <p>Convenience: in this store nothing lives in the default graph (every
 * resource is its own named graph), so a bare {@code ?s ?p ?o} would always be
 * empty. The default graph is therefore served as the ACP-filtered UNION of
 * the named graphs — {@code GRAPH ?g} still works and still enumerates only
 * readable resources.
 *
 * <p><strong>{@code ?iri=<resource>} — the per-BeakGraph endpoint.</strong>
 * With {@code iri} naming a storage-resident BeakGraph (HDF5) resource, the
 * query runs against THAT FILE's own dataset, stood up on the fly from the
 * {@code BeakGraphPool} — every {@code .h5} in a storage is a SPARQL endpoint
 * at {@code /rdf2?iri=<its-uri>}. This is the return of the old storage's
 * Raptor servlet, rebuilt under the C2 deletion's conditions: the target is
 * named by its LWS URI and resolved through the registry (never a
 * client-supplied path), reading it demands the caller's own ACP
 * {@code Read} (a refusal answers 404, so an unauthorized resource is not
 * even discoverable), and execution is bounded by the same parse-as-query and
 * timeout rules as the store view.
 */
public class LwsSparqlServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(LwsSparqlServlet.class);

    /** Cap on a POSTed query body — a SPARQL query is text, this is generous. */
    private static final int MAX_QUERY_BYTES = 256 * 1024;

    /** Runaway-query guard; the store is shared with live LWS requests. */
    private static final long TIMEOUT_SECONDS = 60;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        serve(request, response, request.getParameter("query"));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String contentType = request.getContentType();
        String query;
        if (contentType != null && contentType.toLowerCase().startsWith("application/sparql-query")) {
            byte[] body = request.getInputStream().readNBytes(MAX_QUERY_BYTES + 1);
            if (body.length > MAX_QUERY_BYTES) {
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Query too large");
                return;
            }
            query = new String(body, StandardCharsets.UTF_8);
        } else {
            // application/x-www-form-urlencoded — the standard SPARQL-over-POST form.
            query = request.getParameter("query");
        }
        serve(request, response, query);
    }

    private void serve(HttpServletRequest request, HttpServletResponse response, String queryString)
            throws IOException {
        if (LwsSettings.get().storages().isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "No W3C LWS storages are configured (settings.ttl :hasLWSStorage)");
            return;
        }
        if (queryString == null || queryString.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "a SPARQL query is required (?query= or POST application/sparql-query)");
            return;
        }
        Query query;
        try {
            query = QueryFactory.create(queryString);
        } catch (QueryParseException ex) {
            // The client's own query text; echoing the parse position is safe and kind.
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "query parse error: " + ex.getMessage());
            return;
        }

        // Who is asking. The session's WebID when signed in, else the public
        // agent — the same identity model every LWS HTTP request resolves to.
        HalcyonPrincipal hp = RequestPrincipal.resolve(request, response);
        AgentContext agent = RequestPrincipal.isSignedIn(hp) && hp.getUserURI() != null
                ? new AgentContext(hp.getUserURI(), null, null, null)
                : AgentContext.PUBLIC;

        String iri = request.getParameter("iri");
        if (iri != null && !iri.isBlank()) {
            serveBeakGraph(response, query, iri.trim(), agent, request.getHeader("Accept"));
            return;
        }

        LwsStore store = LwsStore.get();
        // Fresh per request (see AcpSecurityEvaluator's contract): the secured
        // view for THIS agent, over the raw store it wraps.
        AcpSecuredDatasetGraph secured = new AcpSecuredDatasetGraph(
                store.raw().asDatasetGraph(), new AcpSecurityEvaluator(agent, new AcpEngine(store)));
        // Nothing lives in the store's default graph, so serve it as the
        // secured UNION — built over the secured view, never the base.
        DatasetGraph queryable = new DatasetGraphWrapper(secured) {
            @Override
            public Graph getDefaultGraph() {
                return secured.getUnionGraph();
            }
        };
        Dataset dataset = DatasetFactory.wrap(queryable);

        String accept = request.getHeader("Accept");
        try {
            // The whole execution AND serialization ride one read transaction:
            // result iteration touches TDB lazily, so streaming outside the
            // transaction would read unprotected.
            store.read(() -> {
                try (QueryExecution qe = QueryExecution.dataset(dataset).query(query)
                        .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()) {
                    respond(response, qe, accept);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            // The client went away mid-stream; nothing to answer.
        } catch (RuntimeException ex) {
            logger.warn("/rdf2 query failed", ex);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "query failed");
            }
        }
    }

    /**
     * The per-file mode: SPARQL over ONE BeakGraph resource's own dataset.
     * Registry-resolved, ACP-gated, pool-served — see the class javadoc for
     * why each of those is a condition of this feature existing at all.
     */
    private void serveBeakGraph(HttpServletResponse response, Query query, String iri,
            AgentContext agent, String accept) throws IOException {
        LwsStorageConfig cfg = storageOf(iri);
        if (cfg == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "no such queryable resource");
            return;
        }
        LwsStore store = LwsStore.get();
        // Resolve and authorize in one short read transaction. An unreadable
        // resource answers exactly like a missing one: 404, no discovery.
        LwsResource r = store.read(() ->
                new AcpEngine(store).allows(agent, iri, AccessMode.READ)
                        ? new ResourceRegistry(store, cfg).find(iri).orElse(null)
                        : null);
        if (r == null || r.isContainer() || r.storageKey() == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "no such queryable resource");
            return;
        }
        boolean hdf5 = "application/x-hdf5".equalsIgnoreCase(r.mediaType())
                || "application/x-hdf".equalsIgnoreCase(r.mediaType())
                || ".h5".equalsIgnoreCase(r.ext());
        if (!hdf5) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "the target is not a BeakGraph (HDF5) resource");
            return;
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
            logger.warn("/rdf2 BeakGraph query of {} failed", iri, ex);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY,
                        "the resource could not be opened as a BeakGraph");
            }
        } finally {
            if (bg != null) {
                try {
                    BeakGraphPool.getPool().returnObject(key, bg);
                } catch (Exception ex) {
                    logger.warn("BeakGraph pool return failed for {}", iri, ex);
                }
            }
        }
    }

    /** The configured storage a URI belongs to, or {@code null} (same rule as SaveStackServlet). */
    private static LwsStorageConfig storageOf(String uri) {
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            if (uri.startsWith(cfg.baseUri() + "/")) {
                return cfg;
            }
        }
        return null;
    }

    /** Serialize by query type, negotiating the common formats plainly. */
    private static void respond(HttpServletResponse response, QueryExecution qe, String accept)
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
        // CONSTRUCT / DESCRIBE — a graph either way.
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
