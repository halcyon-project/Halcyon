package com.ebremer.lws.capability;

import com.ebremer.lws.config.LwsStorageConfig;

/**
 * A capability a storage offers beyond plain LWS read/write — the IIIF Image
 * service, a per-resource query interface, a future thumbnailer or full-text
 * index. It exists so that "add a capability" means "write one class and register
 * it", instead of editing the router, the config, the servlet dispatch and the
 * storage description each time.
 *
 * <p>A capability is one of:
 * <ul>
 *   <li>{@link ResourceCapability} — attached to a resource's own URL and activated
 *       by a request marker (the per-resource SPARQL shape). This is the only
 *       specialization wired in Stage 1.
 *   <li>{@code EndpointCapability} — mounted at a reserved sub-path of the storage
 *       (the IIIF {@code .iiif} shape). Introduced in Stage 2, when IIIF migrates
 *       onto this SPI.
 *   <li>a bare {@code StorageCapability} — advertisement only: it names a service
 *       the storage points at but does not itself handle (e.g. the store-wide SPARQL
 *       endpoint, a separate app-tier route). Wired in Stage 3.
 * </ul>
 *
 * <p>The one thing every capability contributes, whichever shape, is how it appears
 * in the storage description; routing and handling are added by the sub-interfaces.
 * See {@code PLAN-CAPABILITY.md} in this module for the full design and the staged
 * migration.
 */
public interface StorageCapability {

    /**
     * How this capability advertises itself in the storage description, for the given
     * storage, or {@code null} to advertise nothing — a capability that is installed
     * but deliberately undocumented.
     *
     * <p>Returning a descriptor here is the <em>only</em> hook a capability needs to
     * appear in the description; {@link com.ebremer.lws.json.LwsJson} renders the
     * returned value, so capability authors never touch the JSON and the "context is
     * not yet published, emit it literally" discipline stays in one place.
     *
     * <p><strong>Stage 1:</strong> the per-resource SPARQL capability returns
     * {@code null} — the per-resource query surface is deliberately unadvertised (see
     * PLAN-CAPABILITY.md, D4) — and nothing calls this yet: the wiring into
     * {@code LwsJson} lands with the advertised capabilities in Stages 2–3. The method
     * is on the interface now so the SPI is stable across the stages.
     */
    CapabilityDescriptor descriptor(LwsStorageConfig cfg);
}
