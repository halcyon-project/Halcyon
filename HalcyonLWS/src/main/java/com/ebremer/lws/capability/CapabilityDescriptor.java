package com.ebremer.lws.capability;

import java.util.List;

/**
 * What a capability contributes to the storage description: an optional {@code service}
 * entry, an optional {@code capability} entry, or both. Either may be {@code null}.
 * {@link com.ebremer.lws.json.LwsJson} renders these into {@code application/lws+json}.
 *
 * <p>The description already separates {@code service[]} (endpoints a client calls)
 * from {@code capability[]} (protocol features / dialects), and a single capability may
 * belong in both — IIIF advertises an {@code ImageService} service <em>and</em> an
 * {@code http://iiif.io/api/image} capability entry. Hence the two optional halves.
 *
 * <p>Type IRIs are supplied by the capability, not this module: an implementation
 * passes whatever IRI it wants (an external standard such as
 * {@code http://iiif.io/api/image}, or a Halcyon-namespaced
 * {@code https://halcyon.is/ns/…} term while the LWS vocabulary is unsettled), and the
 * renderer emits it verbatim. The module bakes in no capability vocabulary of its own.
 *
 * <p><em>Not wired until Stages 2–3.</em> Stage 1's only capability (per-resource
 * SPARQL) is unadvertised, so it returns {@code null} from
 * {@link StorageCapability#descriptor}; this type exists now so the SPI is stable.
 */
public record CapabilityDescriptor(ServiceEntry service, CapabilityEntry capability) {

    public static CapabilityDescriptor service(ServiceEntry s) {
        return new CapabilityDescriptor(s, null);
    }

    public static CapabilityDescriptor capability(CapabilityEntry c) {
        return new CapabilityDescriptor(null, c);
    }

    public static CapabilityDescriptor of(ServiceEntry s, CapabilityEntry c) {
        return new CapabilityDescriptor(s, c);
    }

    /**
     * An entry in the description's {@code service[]} — an endpoint a client calls.
     *
     * @param conformsTo profile/standard IRIs, or {@code null}/empty for none
     * @param note       a human-readable hint, or {@code null}
     */
    public record ServiceEntry(String type, String serviceEndpoint,
            List<String> conformsTo, String note) {
    }

    /**
     * An entry in the description's {@code capability[]} — a protocol feature/dialect.
     *
     * @param serviceEndpoint the endpoint the feature is exercised at, or {@code null}
     * @param note            a human-readable hint, or {@code null}
     */
    public record CapabilityEntry(String type, String serviceEndpoint, String note) {
    }
}
