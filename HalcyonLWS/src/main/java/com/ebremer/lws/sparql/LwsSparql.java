package com.ebremer.lws.sparql;

import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.acp.AcpSecuredDatasetGraph;
import com.ebremer.lws.acp.AcpSecurityEvaluator;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.store.LwsStore;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphWrapper;

/**
 * SPARQL over the W3C LWS store, ACP-filtered to a given agent — the reusable engine a host exposes
 * as a SPARQL endpoint.
 *
 * <p>Everything here is pure Jena plus the module's own ACP/store machinery: it depends on neither
 * how a host authenticates the agent (bearer, session, …) nor how it routes the endpoint. The host
 * resolves the {@link AgentContext} and owns the HTTP surface; this owns the query view and result
 * serialization. (The module deliberately does not <em>route</em> a SPARQL endpoint — SPARQL is not
 * an LWS surface — so this is offered as a reusable helper, not a mounted service.)
 *
 * <p><strong>Never the raw store.</strong> Both views are the CALLER's ACP-secured view
 * ({@link AcpSecuredDatasetGraph}): a query answers exactly what that agent's own LWS {@code GET}s
 * could fetch, the internal graphs (system, ACP, subscriptions, keys, sharing) are unconditionally
 * invisible, and a resource the agent may not read is not discoverable at all. The evaluator is
 * built fresh per call, per {@link AcpSecurityEvaluator}'s one-instance-per-request contract, so the
 * ACP decision is always live.
 *
 * <p>{@link #secured} exposes the named graphs (query with {@code GRAPH ?g {…}}); {@link #securedUnion}
 * additionally serves the default graph as the ACP-filtered union of them, so a bare {@code ?s ?p ?o}
 * is meaningful in a store where every resource is its own named graph and nothing lives in the
 * default graph.
 */
public final class LwsSparql {

    private LwsSparql() {
    }

    /** The caller's ACP-secured view of the store, as a {@link DatasetGraph}. */
    public static AcpSecuredDatasetGraph securedGraph(LwsStore store, AgentContext agent) {
        return new AcpSecuredDatasetGraph(store.raw().asDatasetGraph(),
                new AcpSecurityEvaluator(agent, new AcpEngine(store)));
    }

    /** The caller's ACP-secured view as a queryable {@link Dataset} (named graphs). */
    public static Dataset secured(LwsStore store, AgentContext agent) {
        return DatasetFactory.wrap(securedGraph(store, agent));
    }

    /**
     * The caller's ACP-secured view, with the default graph served as the ACP-filtered UNION of the
     * named graphs. In this store nothing lives in the default graph (every resource is its own
     * named graph), so a bare {@code ?s ?p ?o} would otherwise always be empty; the union keeps it
     * meaningful, while {@code GRAPH ?g} still enumerates only readable resources.
     */
    public static Dataset securedUnion(LwsStore store, AgentContext agent) {
        AcpSecuredDatasetGraph secured = securedGraph(store, agent);
        // Built over the secured view, never the base — so the union is the ACP-filtered union.
        DatasetGraph queryable = new DatasetGraphWrapper(secured) {
            @Override
            public Graph getDefaultGraph() {
                return secured.getUnionGraph();
            }
        };
        return DatasetFactory.wrap(queryable);
    }

    /**
     * Serialize a query execution to the response, negotiating the common formats from
     * {@code accept}: SELECT/ASK as SPARQL results (JSON default, or XML/CSV/TSV), CONSTRUCT/DESCRIBE
     * as an RDF graph (Turtle default, or N-Triples).
     *
     * <p>The whole execution AND this serialization must ride one read transaction: result iteration
     * touches the store lazily, so streaming outside the transaction would read unprotected. The
     * caller owns that transaction; this only iterates and writes.
     */
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
