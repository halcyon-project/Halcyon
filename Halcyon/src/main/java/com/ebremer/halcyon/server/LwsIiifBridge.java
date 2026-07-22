package com.ebremer.halcyon.server;

import com.ebremer.halcyon.imagebox.ImageServer;
import com.ebremer.halcyon.server.utils.ImageReaderPoolFactory;
import com.ebremer.lws.capability.CapabilityDescriptor;
import com.ebremer.lws.capability.CapabilityRequest;
import com.ebremer.lws.capability.EndpointCapability;
import com.ebremer.lws.config.LwsStorageConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The LWS storages' IIIF Image service, as an {@link EndpointCapability}: Halcyon's existing IIIF
 * engine (tile cache, reader pool, output budgets) mounted at each storage's reserved {@code .iiif}
 * endpoint and pointed at an LWS resource's blob.
 *
 * <p>The module owns the policy — it routes {@code .iiif}, confines the identifier to the storage,
 * demands {@code acl:Read} through ACP, and resolves the content path — so this contributes only the
 * reserved-path name ({@link #reservedPath}), the image-identity extraction ({@link #targetResource}),
 * the storage-description advertisement ({@link #descriptor}), and the imaging itself
 * ({@link #serve}).
 *
 * <p>Imaging without weakening H9 (the reader pool refuses {@code file:} identifiers because the IIIF
 * URL is attacker-controlled): each (resource, content path) pair gets an unguessable synthetic
 * {@code urn:lws-iiif:} key, registered through the pool's trusted-source channel and used as the
 * engine-side identity. The key never leaves the server, so the public {@code /iiif} servlet cannot
 * name it; a replaced blob gets a fresh key, so tile and reader caches can never serve stale content.
 */
public final class LwsIiifBridge implements EndpointCapability {

    /** The IIIF Image API protocol IRI (the value info.json carries as {@code "protocol"}). */
    private static final String IIIF_IMAGE_API = "http://iiif.io/api/image";

    private final ImageServer engine = new ImageServer();
    /** (imageUri | content path) → the synthetic engine key registered for it. */
    private final ConcurrentHashMap<String, URI> keys = new ConcurrentHashMap<>();

    @Override
    public String reservedPath() {
        return LwsStorageConfig.IIIF;
    }

    /**
     * The image identity carried in the {@code iiif} parameter (the module confines it to this
     * storage). The parameter is a full IIIF Image API URL —
     * {@code {imageUri}/{region}/{size}/{rotation}/{quality}.{format}} or {@code {imageUri}/info.json}
     * — and the identity is everything before those segments. {@code null} for a missing or
     * unparseable parameter (the module answers 400).
     */
    @Override
    public String targetResource(HttpServletRequest req, LwsStorageConfig cfg) {
        String param = req.getParameter("iiif");
        if (param == null || param.isBlank()) {
            return null;
        }
        return iiifImageUri(param);
    }

    @Override
    public void serve(CapabilityRequest cr) {
        String imageUri = cr.resource().uri();
        Path content = cr.content();
        URI key = keys.computeIfAbsent(imageUri + "|" + content, k -> {
            URI minted = URI.create("urn:lws-iiif:" + UUID.randomUUID());
            ImageReaderPoolFactory.registerTrustedSource(minted, content);
            return minted;
        });
        engine.serve(cr.req(), cr.resp(), key);
    }

    /**
     * Advertise the IIIF Image service: an {@code ImageService} service entry and the
     * {@code http://iiif.io/api/image} capability entry carrying the query dialect.
     */
    @Override
    public CapabilityDescriptor descriptor(LwsStorageConfig cfg) {
        String endpoint = cfg.iiifUri();
        return CapabilityDescriptor.of(
                new CapabilityDescriptor.ServiceEntry("ImageService", endpoint,
                        List.of(IIIF_IMAGE_API), null),
                new CapabilityDescriptor.CapabilityEntry(IIIF_IMAGE_API, endpoint,
                        "query dialect: ?iiif={imageUri}/{region}/{size}/{rotation}/{quality}.{format}"
                        + " or ?iiif={imageUri}/info.json; {imageUri} must be a data resource of this"
                        + " storage"));
    }

    /**
     * The image identity inside a IIIF Image API URL: everything before {@code /info.json}, or before
     * the four request segments ({@code /{region}/{size}/{rotation}/{quality}.{format}}). Returns
     * {@code null} when the URL has no such shape. (Moved here from the LWS module when IIIF became a
     * capability.)
     */
    public static String iiifImageUri(String iiifUrl) {
        String u = iiifUrl.trim();
        if (u.endsWith("/info.json")) {
            String id = u.substring(0, u.length() - "/info.json".length());
            return id.isEmpty() ? null : id;
        }
        int seen = 0;
        for (int i = u.length() - 1; i >= 0; i--) {
            if (u.charAt(i) == '/') {
                seen++;
                if (seen == 4) {
                    return i == 0 ? null : u.substring(0, i);
                }
            }
        }
        return null;
    }
}
