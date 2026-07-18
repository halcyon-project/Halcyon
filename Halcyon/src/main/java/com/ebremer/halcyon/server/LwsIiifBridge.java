package com.ebremer.halcyon.server;

import com.ebremer.halcyon.imagebox.ImageServer;
import com.ebremer.halcyon.server.utils.ImageReaderPoolFactory;
import com.ebremer.lws.iiif.IiifService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The imaging half of the LWS storages' {@code .iiif} endpoint: Halcyon's
 * existing IIIF engine (tile cache, reader pool, output budgets) pointed at
 * an LWS resource's blob.
 *
 * <p>The storage servlet has already done the policy work by the time this
 * runs — confined the identifier to its own storage, demanded {@code acl:Read}
 * through ACP, and resolved the content path. What remains is wiring those
 * bytes into the engine without weakening H9 (the reader pool refuses
 * {@code file:} identifiers because {@code ?iiif=} is attacker-controlled):
 * each (resource, content path) pair gets an unguessable synthetic
 * {@code urn:lws-iiif:} key, registered through the pool's trusted-source
 * channel and used as the engine-side identity. The key never leaves the
 * server, so the public {@code /iiif} servlet cannot name it; a replaced
 * blob gets a fresh key, so tile and reader caches can never serve stale
 * content.
 */
public final class LwsIiifBridge implements IiifService {

    private final ImageServer engine = new ImageServer();
    /** (imageUri | content path) → the synthetic engine key registered for it. */
    private final ConcurrentHashMap<String, URI> keys = new ConcurrentHashMap<>();

    @Override
    public void serve(HttpServletRequest req, HttpServletResponse resp,
            String imageUri, Path content, String ext) {
        URI key = keys.computeIfAbsent(imageUri + "|" + content, k -> {
            URI minted = URI.create("urn:lws-iiif:" + UUID.randomUUID());
            ImageReaderPoolFactory.registerTrustedSource(minted, content);
            return minted;
        });
        engine.serve(req, resp, key);
    }
}
