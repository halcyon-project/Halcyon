package com.ebremer.lws.capability;

import com.ebremer.lws.store.LwsResource;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * A capability attached to a resource's own URL, activated by a request marker.
 *
 * <p>The per-resource SPARQL endpoint is the motivating case: {@code GET
 * {resource}?query=…}, {@code POST {resource}} with {@code application/sparql-query},
 * and the RFC 10008 {@code QUERY} method with {@code application/sparql-query} are all
 * answered from the resource's own graph; anything else on that URL is a normal LWS
 * request. Bringing this into the module retires the app-tier {@code
 * LwsResourceSparqlFilter} and deletes the authorization/resolution it duplicated.
 *
 * <p>The claim is split into two questions so the servlet resolves a resource only
 * when it might matter — {@link #handles} looks at the request alone (cheap, on every
 * resource request), {@link #claims} looks at the already-resolved resource (only when
 * {@code handles} said yes). Both are forbidden the request body, so a request that is
 * <em>not</em> claimed reaches normal LWS handling with its entity intact.
 *
 * <p><strong>The module owns authorization.</strong> Between {@code claims} returning
 * true and {@link #serve}, the servlet establishes existence, demands {@code acl:Read}
 * through ACP, and resolves the content to a local path — the same envelope a normal
 * {@code GET} runs in. A capability therefore cannot obtain a resource except
 * pre-authorized, and cannot widen access.
 */
public interface ResourceCapability extends StorageCapability {

    /**
     * Does this request carry this capability's marker? Request-only and cheap: the
     * method, the {@code Content-Type} header, and query-string parameters — never the
     * body, never a resolved resource. The servlet calls this on every GET/POST/QUERY
     * to a resource, and only resolves the resource if some capability says yes.
     *
     * <p>Must not consume the body. In particular, for a {@code POST} it may read the
     * {@code Content-Type} but must not call {@code getParameter} (which would parse a
     * form body); query-string parameters are safe only for methods without a body.
     */
    boolean handles(HttpServletRequest req);

    /**
     * Given the resolved resource, does this capability actually claim the request?
     * Called only after {@link #handles} returned true, so the marker is already
     * present. Returning false passes the request through to normal LWS handling.
     *
     * <p>Sees the resource's metadata (type, media type, extension) and the request's
     * method/headers/parameters only — <strong>never the body.</strong> The SPARQL
     * capability claims by marker certainty: an unambiguous {@code POST}/{@code QUERY}
     * {@code application/sparql-query} is claimed on any data resource (and
     * {@link #serve} answers 415 if it is not actually queryable), while an ambiguous
     * {@code GET ?query=} — a stray parameter must not break a plain fetch — is claimed
     * only when the resource is queryable.
     */
    boolean claims(LwsResource resource, HttpServletRequest req);

    /**
     * Serve the claimed request. The {@link CapabilityRequest}'s resource has already
     * been existence-checked, {@code acl:Read}-authorized and content-resolved by the
     * module; run over its content and do not widen.
     *
     * <p>A claim made on the unambiguous {@code POST}/{@code QUERY}
     * {@code application/sparql-query} marker may reach here on a resource that is
     * readable but not queryable (a SPARQL POST to a JPEG). That is answered
     * <strong>415 Unsupported Media Type</strong>. Because the module authorized
     * {@code acl:Read} first, a 415 is only ever seen by an agent that can already read
     * the resource, so it is no oracle; a missing or unreadable target was turned away
     * as 404/401 before {@code serve} ran.
     */
    void serve(CapabilityRequest cr) throws IOException;
}
