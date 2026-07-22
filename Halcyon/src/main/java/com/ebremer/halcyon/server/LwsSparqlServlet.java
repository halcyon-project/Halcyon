package com.ebremer.halcyon.server;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.acp.AcpSecuredDatasetGraph;
import com.ebremer.lws.acp.AcpSecurityEvaluator;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.store.LwsStore;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
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
 * query runs against THAT FILE's own dataset via {@link BeakGraphEndpoint} —
 * every {@code .h5} in a storage is a SPARQL endpoint at {@code /rdf2?iri=<its-uri>}
 * (and, through {@link LwsResourceSparqlFilter}, at its own URL directly).
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

        // Who is asking. The session's WebID when signed in, else the public
        // agent — the same identity model every LWS HTTP request resolves to.
        HalcyonPrincipal hp = RequestPrincipal.resolve(request, response);
        AgentContext agent = RequestPrincipal.isSignedIn(hp) && hp.getUserURI() != null
                ? new AgentContext(hp.getUserURI(), null, null, null)
                : AgentContext.PUBLIC;
        String accept = request.getHeader("Accept");

        // ?iri= — the per-BeakGraph endpoint (shared with the resource-URL filter).
        String iri = request.getParameter("iri");
        if (iri != null && !iri.isBlank()) {
            switch (BeakGraphEndpoint.serve(response, queryString, iri.trim(), agent, accept)) {
                case NOT_FOUND -> response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "no such queryable resource");
                case NOT_BEAKGRAPH -> response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "the target is not a BeakGraph (HDF5) resource");
                case SERVED -> { }
            }
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

        try {
            // The whole execution AND serialization ride one read transaction:
            // result iteration touches TDB lazily, so streaming outside the
            // transaction would read unprotected.
            store.read(() -> {
                try (QueryExecution qe = QueryExecution.dataset(dataset).query(query)
                        .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()) {
                    BeakGraphEndpoint.respond(response, qe, accept);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            // The client went away mid-stream, OR a lazy step (a federated SERVICE) failed after the
            // response committed — either way it cannot be answered now. Logged for diagnosis: a
            // genuine client disconnect is benign, but a SERVICE failure that surfaces here (its
            // SSLHandshakeException IS an IOException) would otherwise be completely silent.
            logger.debug("/rdf2 response ended early", ex);
        } catch (RuntimeException ex) {
            logger.warn("/rdf2 query failed", ex);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "query failed");
            }
        }
    }
}
