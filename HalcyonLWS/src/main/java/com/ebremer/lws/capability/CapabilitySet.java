package com.ebremer.lws.capability;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * The capabilities installed on a storage servlet: the servlet holds one of these and
 * asks it the routing questions, rather than knowing any capability by name.
 *
 * <p>Constructed once, at boot, from whatever the host installs. Stage 1 partitions out
 * the {@link ResourceCapability}s and answers {@link #candidate}; endpoint routing and
 * the storage-description descriptors arrive in Stages 2–3. An empty set (the default)
 * means the storage behaves exactly as a storage with no capabilities.
 *
 * <p>Selection is <strong>first-match by registration order</strong>: the first
 * resource capability whose {@link ResourceCapability#handles} accepts the request
 * wins. Capabilities are expected to have disjoint markers; where they might overlap,
 * order is the defined tie-break.
 */
public final class CapabilitySet {

    /** A storage with no installed capabilities. */
    public static final CapabilitySet EMPTY = new CapabilitySet(List.of());

    private final List<ResourceCapability> resource;

    public CapabilitySet(List<? extends StorageCapability> capabilities) {
        List<ResourceCapability> r = new ArrayList<>();
        for (StorageCapability c : capabilities) {
            if (c instanceof ResourceCapability rc) {
                r.add(rc);
            }
            // EndpointCapability is partitioned here in Stage 2; a bare StorageCapability
            // contributes only a descriptor (collected in Stage 3).
        }
        this.resource = List.copyOf(r);
    }

    /** Convenience for a fixed list of capabilities. */
    public static CapabilitySet of(StorageCapability... capabilities) {
        return new CapabilitySet(List.of(capabilities));
    }

    /**
     * The first resource capability whose marker matches this request, or {@code null}
     * if none does. Cheap: it consults {@link ResourceCapability#handles} only (request
     * headers/parameters, never the body, never a resolved resource), so the servlet
     * can gate resource resolution on a non-null result.
     */
    public ResourceCapability candidate(HttpServletRequest req) {
        for (ResourceCapability c : resource) {
            if (c.handles(req)) {
                return c;
            }
        }
        return null;
    }

    /** Whether any resource capability is installed (a fast bypass for the servlet). */
    public boolean hasResourceCapabilities() {
        return !resource.isEmpty();
    }
}
