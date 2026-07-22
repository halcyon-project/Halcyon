package com.ebremer.lws.capability;

import com.ebremer.lws.config.LwsStorageConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * A capability mounted at a reserved sub-path of the storage, e.g. {@code .iiif}.
 *
 * <p>The module owns all the policy: it recognises the reserved path, asks the capability
 * which resource the request targets, <strong>confines that resource to this storage</strong>,
 * demands {@code acl:Read} on it through ACP, resolves its bytes in the content store, and only
 * then calls {@link #serve}. The capability never sees an unauthorized request and cannot resolve
 * a resource itself — so it cannot re-implement, or weaken, the authorization the module enforces.
 *
 * <p>Endpoint capabilities are served over {@code GET} (plus {@code OPTIONS}); the module answers
 * the HTTP-method envelope generically, so an implementation only writes the {@code GET} response.
 */
public interface EndpointCapability extends StorageCapability {

    /**
     * The reserved path segment under the storage root, dot-led, e.g. {@code ".iiif"}.
     *
     * <p>Dot-led is not decoration: the slug sanitiser strips leading dots, so no client-created
     * resource can ever collide with it (the same guarantee the core reserved endpoints rely on).
     * {@link CapabilitySet} rejects two capabilities that claim the same segment, at boot.
     */
    String reservedPath();

    /**
     * Which resource of this storage this request targets, as a full LWS URI, or {@code null} if
     * the request is malformed (the module answers 400).
     *
     * <p>Extraction only — no authorization, no I/O. For IIIF this parses the image identity out of
     * the compound IIIF URL in the {@code iiif} parameter. Whatever it returns, the module still
     * confines it to {@code cfg.baseUri()} and authorizes it, so a buggy extractor fails closed
     * (400/404), never open.
     */
    String targetResource(HttpServletRequest req, LwsStorageConfig cfg);

    /**
     * Serve the request. By the time this runs the target resource in {@link CapabilityRequest} has
     * been confirmed to exist, to be a data resource of this storage, to be readable by the agent,
     * and to have its content resolved to a local path. Serve derivatives of that content only; do
     * not widen the request.
     */
    void serve(CapabilityRequest cr) throws IOException;
}
