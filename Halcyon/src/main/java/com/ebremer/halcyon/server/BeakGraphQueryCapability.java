package com.ebremer.halcyon.server;

import com.ebremer.lws.capability.CapabilityDescriptor;
import com.ebremer.lws.capability.CapabilityRequest;
import com.ebremer.lws.capability.ResourceCapability;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.store.LwsResource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The per-resource SPARQL endpoint, as an LWS {@link ResourceCapability}: every BeakGraph (HDF5)
 * resource is a SPARQL endpoint at its own URL, which is what makes {@code SERVICE <…/foo.h5> {…}}
 * federation work. It replaces the app-tier {@code LwsResourceSparqlFilter}, which sat in front of
 * the storage servlet and re-implemented the module's resolve/authorize pipeline; here the module
 * resolves and authorizes, and this only executes.
 *
 * <p>Three request bindings of the SPARQL query operation are accepted, covering what Jena's ARQ
 * {@code SERVICE} sends plus the RFC 10008 method (SPARQL 1.2 Protocol, §Query operation):
 * <ul>
 *   <li>{@code GET {resource}?query=…}
 *   <li>{@code POST {resource}} with {@code Content-Type: application/sparql-query} (body = query)
 *   <li>{@code QUERY {resource}} with {@code Content-Type: application/sparql-query} (body = query)
 * </ul>
 * A POST with {@code application/x-www-form-urlencoded} is not claimed (it is not what {@code SERVICE}
 * sends and would consume the create path's body); the store-wide {@code /rdf2} endpoint takes that
 * form.
 *
 * <p><strong>Queryable = BeakGraph only, for now.</strong> {@link #isQueryable} is the single seam
 * for that policy; it is expected to grow to HDT and later to capped small Turtle (see
 * PLAN-CAPABILITY.md, D1) without touching the SPI. A SPARQL POST/QUERY to a readable non-queryable
 * resource is answered 415; a {@code ?query=} GET to one passes through to a normal fetch.
 *
 * <p>Unadvertised: the per-resource query surface is deliberately kept out of the storage
 * description (only the store-wide {@code /rdf2} is advertised), so {@link #descriptor} returns
 * {@code null}. See PLAN-CAPABILITY.md, D4.
 */
public final class BeakGraphQueryCapability implements ResourceCapability {

    /** Cap on a POSTed/QUERYed query body — a SPARQL query is text, this is generous. */
    private static final int MAX_QUERY_BYTES = 256 * 1024;

    @Override
    public boolean handles(HttpServletRequest req) {
        String method = req.getMethod();
        if ("GET".equalsIgnoreCase(method)) {
            // Query-string parameter only — safe on a GET (no body), and never call getParameter on
            // a POST (that would parse and consume a form body meant for the create path).
            String q = req.getParameter("query");
            return q != null && !q.isBlank();
        }
        if ("POST".equalsIgnoreCase(method) || "QUERY".equalsIgnoreCase(method)) {
            // Content-Type header only — the body is left untouched until serve().
            return isSparqlQueryContentType(req.getContentType());
        }
        return false;
    }

    @Override
    public boolean claims(LwsResource resource, HttpServletRequest req) {
        if ("GET".equalsIgnoreCase(req.getMethod())) {
            // The ?query= marker is ambiguous — a stray parameter must not break a plain fetch — so
            // claim only a genuinely queryable resource; otherwise pass through.
            return isQueryable(resource);
        }
        // POST/QUERY application/sparql-query is unambiguous: claim any data resource and let serve()
        // answer 415 if it is not queryable. A container is a create (POST) / 405 (QUERY) — pass through.
        return !resource.isContainer();
    }

    @Override
    public void serve(CapabilityRequest cr) throws IOException {
        HttpServletRequest req = cr.req();
        HttpServletResponse resp = cr.resp();

        if (!isQueryable(cr.resource())) {
            // Reachable only via the unambiguous POST/QUERY marker (a non-queryable GET passed
            // through). Read was already authorized by the module, so this discloses nothing.
            resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "this resource does not accept a SPARQL query; only BeakGraph resources are queryable");
            return;
        }

        String queryString;
        if ("GET".equalsIgnoreCase(req.getMethod())) {
            queryString = req.getParameter("query");
        } else {
            byte[] body = req.getInputStream().readNBytes(MAX_QUERY_BYTES + 1);
            if (body.length > MAX_QUERY_BYTES) {
                resp.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "query too large");
                return;
            }
            queryString = new String(body, StandardCharsets.UTF_8);
        }
        if (queryString == null || queryString.isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "a SPARQL query is required");
            return;
        }

        // Content is already resolved and Read-authorized by the module: execute over it directly.
        BeakGraphEndpoint.serveContent(resp, queryString, cr.content(),
                req.getHeader("Accept"), cr.resource().uri());
    }

    /** Unadvertised per-resource surface (D4): nothing goes into the storage description. */
    @Override
    public CapabilityDescriptor descriptor(LwsStorageConfig cfg) {
        return null;
    }

    /**
     * The queryability seam. Today: BeakGraph (HDF5) only. Later (PLAN-CAPABILITY.md, D1):
     * {@code || isHdt(r) || (isSmallTurtle(r) && withinTripleCap(r))} — grown here, around a single
     * predicate, never in the protocol.
     */
    static boolean isQueryable(LwsResource resource) {
        return BeakGraphEndpoint.isBeakGraph(resource);
    }

    /** Whether a Content-Type is {@code application/sparql-query} (media-type parameters allowed). */
    static boolean isSparqlQueryContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String ct = contentType.trim().toLowerCase(Locale.ROOT);
        int semi = ct.indexOf(';');
        String base = (semi >= 0 ? ct.substring(0, semi) : ct).trim();
        return base.equals("application/sparql-query");
    }
}
