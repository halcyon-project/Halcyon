package com.ebremer.lws.capability;

import com.ebremer.lws.config.LwsStorageConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The capabilities installed on a storage servlet: the servlet holds one of these and asks it the
 * routing questions, rather than knowing any capability by name.
 *
 * <p>Constructed once, at boot, from whatever the host installs, and partitioned by shape:
 * <ul>
 *   <li>{@link ResourceCapability}s answer {@link #candidate} (per-resource query, activated by a
 *       request marker);
 *   <li>{@link EndpointCapability}s answer {@link #endpointFor} (a reserved sub-path such as
 *       {@code .iiif});
 *   <li>every capability, whichever shape, contributes to {@link #descriptors} for the storage
 *       description.
 * </ul>
 * An empty set (the default) means the storage behaves exactly as one with no capabilities.
 *
 * <p>Resource selection is <strong>first-match by registration order</strong>. Two endpoint
 * capabilities claiming the same reserved path is a boot-time {@link IllegalStateException} — fail
 * loudly, as a duplicate storage mount does.
 */
public final class CapabilitySet {

    /** A storage with no installed capabilities. */
    public static final CapabilitySet EMPTY = new CapabilitySet(List.of());

    private final List<StorageCapability> all;
    private final List<ResourceCapability> resource;
    private final Map<String, EndpointCapability> endpoint;

    public CapabilitySet(List<? extends StorageCapability> capabilities) {
        List<StorageCapability> a = new ArrayList<>();
        List<ResourceCapability> r = new ArrayList<>();
        Map<String, EndpointCapability> e = new LinkedHashMap<>();
        for (StorageCapability c : capabilities) {
            a.add(c);
            if (c instanceof ResourceCapability rc) {
                r.add(rc);
            }
            if (c instanceof EndpointCapability ec) {
                if (e.putIfAbsent(ec.reservedPath(), ec) != null) {
                    throw new IllegalStateException(
                            "two capabilities claim the reserved path " + ec.reservedPath());
                }
            }
        }
        this.all = List.copyOf(a);
        this.resource = List.copyOf(r);
        this.endpoint = e;
    }

    /** Convenience for a fixed list of capabilities. */
    public static CapabilitySet of(StorageCapability... capabilities) {
        return new CapabilitySet(List.of(capabilities));
    }

    /**
     * The first resource capability whose marker matches this request, or {@code null} if none
     * does. Cheap: it consults {@link ResourceCapability#handles} only (request headers/parameters,
     * never the body, never a resolved resource), so the servlet can gate resource resolution on a
     * non-null result.
     */
    public ResourceCapability candidate(HttpServletRequest req) {
        for (ResourceCapability c : resource) {
            if (c.handles(req)) {
                return c;
            }
        }
        return null;
    }

    /**
     * The endpoint capability mounted at {@code rel} (the request path below the storage root, e.g.
     * {@code ".iiif"}), or {@code null} if none is. {@code rel} may be {@code null} (the root).
     */
    public EndpointCapability endpointFor(String rel) {
        return rel == null ? null : endpoint.get(rel);
    }

    /** Every installed capability's storage-description contribution, in registration order. */
    public List<CapabilityDescriptor> descriptors(LwsStorageConfig cfg) {
        List<CapabilityDescriptor> out = new ArrayList<>();
        for (StorageCapability c : all) {
            CapabilityDescriptor d = c.descriptor(cfg);
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    /** Whether any resource capability is installed (a fast bypass for the servlet). */
    public boolean hasResourceCapabilities() {
        return !resource.isEmpty();
    }

    /** Whether any endpoint capability is installed (a fast bypass for the servlet). */
    public boolean hasEndpointCapabilities() {
        return !endpoint.isEmpty();
    }
}
