package com.ebremer.halcyon.server;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.auth.AgentContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Makes each LWS BeakGraph resource its own SPARQL endpoint AT ITS OWN URL. A request carrying a
 * SPARQL query — a {@code ?query=} (GET) or an {@code application/sparql-query} body (POST), exactly
 * what a federated {@code SERVICE <resource-url>} sends — is answered by running that query over the
 * resource's BeakGraph, through the same {@link BeakGraphEndpoint} (registry-resolved, ACP-gated) the
 * {@code /rdf2?iri=} endpoint uses. So {@code SERVICE <…/foo.h5> {…}} works without the
 * {@code /rdf2?iri=} indirection.
 *
 * <p>Everything else passes straight through to {@code LwsServlet} untouched: plain fetches, writes,
 * containers, and non-BeakGraph resources (which ignore {@code ?query=} and serve normally). For a
 * POST it resolves the resource type <em>before</em> reading the body, so a pass-through never
 * consumes the request entity.
 */
public class LwsResourceSparqlFilter implements Filter {

    /** Cap on a POSTed query body — a SPARQL query is text, this is generous. */
    private static final int MAX_QUERY_BYTES = 256 * 1024;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String method = req.getMethod().toUpperCase(Locale.ROOT);

        boolean get = "GET".equals(method);
        boolean sparqlPost = "POST".equals(method) && isSparqlPost(req);
        if (!(get || sparqlPost)) {
            chain.doFilter(request, response);
            return;
        }
        // GET params are safe to read (no body); for a POST we must NOT read the body yet.
        if (get && (req.getParameter("query") == null || req.getParameter("query").isBlank())) {
            chain.doFilter(request, response);
            return;
        }

        String iri = HalcyonSettings.getSettings().getProxyHostName() + req.getRequestURI();
        HalcyonPrincipal hp = RequestPrincipal.resolve(req, resp);
        AgentContext agent = RequestPrincipal.isSignedIn(hp) && hp.getUserURI() != null
                ? new AgentContext(hp.getUserURI(), null, null, null)
                : AgentContext.PUBLIC;

        // POST: decide from the resource type BEFORE touching the body, so a pass-through leaves the
        // entity intact for LwsServlet.
        if (sparqlPost && !BeakGraphEndpoint.isReadableBeakGraph(iri, agent)) {
            chain.doFilter(request, response);
            return;
        }

        String queryString = get ? req.getParameter("query") : readBody(req);
        if (queryString == null || queryString.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        BeakGraphEndpoint.Outcome outcome =
                BeakGraphEndpoint.serve(resp, queryString, iri, agent, req.getHeader("Accept"));
        if (outcome == BeakGraphEndpoint.Outcome.SERVED) {
            return;
        }
        // Not a queryable BeakGraph — serve the resource normally (the query is ignored), per scope.
        chain.doFilter(request, response);
    }

    private static boolean isSparqlPost(HttpServletRequest req) {
        String ct = req.getContentType();
        return ct != null && ct.toLowerCase(Locale.ROOT).startsWith("application/sparql-query");
    }

    private static String readBody(HttpServletRequest req) throws IOException {
        byte[] body = req.getInputStream().readNBytes(MAX_QUERY_BYTES + 1);
        if (body.length > MAX_QUERY_BYTES) {
            return null;
        }
        return new String(body, StandardCharsets.UTF_8);
    }
}
