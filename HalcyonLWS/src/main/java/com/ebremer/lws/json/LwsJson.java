package com.ebremer.lws.json;

import com.ebremer.lws.capability.CapabilityDescriptor;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.vocab.LWS;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.List;

/**
 * Builders for {@code application/lws+json} documents.
 *
 * <p>Built natively rather than through a JSON-LD processor, on purpose. The
 * normative context URI ({@code https://www.w3.org/ns/lws/v1}) is <em>not yet
 * published</em> — it currently 404s — so any processor that tried to resolve it
 * at runtime would fail. The spec still requires us to emit it, so it is emitted
 * as a literal string and never dereferenced.
 *
 * <p>The same bytes serve {@code application/lws+json}, {@code application/ld+json}
 * and {@code application/json}: lws10-core requires the payload to be identical
 * across all three, with only the {@code Content-Type} header varying.
 */
public final class LwsJson {

    private LwsJson() {
    }

    /** Term used in place of {@code lws:Container} etc. — the context maps them. */
    private static final String T_STORAGE = "Storage";
    private static final String T_CONTAINER = "Container";
    private static final String T_CONTAINER_PAGE = "ContainerPage";
    private static final String T_DATA_RESOURCE = "DataResource";
    private static final String T_TYPE_INDEX = "TypeIndex";

    private static JsonObjectBuilder doc(String id, String type) {
        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("@context", LWS.CONTEXT);
        if (id != null) {
            b.add("id", id);
        }
        return b.add("type", type);
    }

    /**
     * The storage description.
     *
     * <p>{@code service} is REQUIRED and MUST contain an entry of type
     * {@code StorageDescription} whose {@code serviceEndpoint} is the description's
     * own URL — that self-reference is how a client confirms it dereferenced the
     * right document.
     *
     * @param descriptors the installed capabilities' contributions. Each may add a
     *     {@code service} entry, a {@code capability} entry, or both — advertised only when
     *     installed, since a capability entry is a contract, not decoration. The IIIF Image
     *     service and the store-wide SPARQL service both arrive this way (an app-tier endpoint
     *     over this storage's data that the module never routes, so it is injected as a
     *     descriptor rather than derived here).
     */
    public static JsonObject storageDescription(LwsStorageConfig cfg,
            List<CapabilityDescriptor> descriptors) {
        JsonArrayBuilder services = Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                        .add("type", "StorageDescription")
                        .add("serviceEndpoint", cfg.descriptionUri()))
                .add(Json.createObjectBuilder()
                        .add("type", "TypeIndexService")
                        .add("serviceEndpoint", cfg.typeIndexUri()))
                .add(Json.createObjectBuilder()
                        .add("type", "TypeSearchService")
                        .add("serviceEndpoint", cfg.typeSearchUri()))
                .add(Json.createObjectBuilder()
                        .add("type", "NotificationService")
                        .add("serviceEndpoint", cfg.subscriptionsUri())
                        .add("subscriptionType", Json.createArrayBuilder()
                                .add("WebhookSubscription")))
                // The DataSharingService: ODRL access requests and grants. Both carry conformsTo
                // for the base access profile, whose constraint operands this storage understands —
                // though it will only install a grant carrying constraints it can actually enforce.
                .add(Json.createObjectBuilder()
                        .add("type", "AccessRequestService")
                        .add("serviceEndpoint", cfg.accessRequestsUri())
                        .add("conformsTo", Json.createArrayBuilder()
                                .add("https://www.w3.org/ns/lws#AccessProfile")))
                .add(Json.createObjectBuilder()
                        .add("type", "AccessGrantService")
                        .add("serviceEndpoint", cfg.accessGrantsUri())
                        .add("conformsTo", Json.createArrayBuilder()
                                .add("https://www.w3.org/ns/lws#AccessProfile")));
        // Capability-contributed service entries (the IIIF ImageService, the store-wide
        // SparqlService, …), advertised only when the capability is installed.
        for (CapabilityDescriptor d : descriptors) {
            if (d.service() != null) {
                services.add(serviceEntry(d.service()));
            }
        }

        // Advertise the patch formats we actually accept. A client is told not to
        // assume PUT or any particular patch format is supported unless it is
        // advertised, so this is the contract, not decoration.
        JsonArrayBuilder capabilities = Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                        .add("type", "https://www.w3.org/ns/lws#PatchSupport")
                        .add("mediaType", Json.createObjectBuilder()
                                .add("application/linkset+json", Json.createArrayBuilder()
                                        .add("application/merge-patch+json"))))
                // RFC 9530 Digest Fields: the algorithms this storage produces (Repr-Digest/
                // Content-Digest) and verifies inbound. A client is told not to assume digest
                // support unless it is advertised, so this is the contract, not decoration.
                .add(Json.createObjectBuilder()
                        .add("type", "https://www.rfc-editor.org/info/rfc9530")
                        .add("algorithm", Json.createArrayBuilder()
                                .add("sha-256").add("sha-512")));
        // Capability-contributed capability entries (e.g. the IIIF query dialect), advertised only
        // when the capability is installed.
        for (CapabilityDescriptor d : descriptors) {
            if (d.capability() != null) {
                capabilities.add(capabilityEntry(d.capability()));
            }
        }

        // The key a subscriber uses to verify a webhook's HTTP Message Signature. It is
        // published here, rather than out of band, so a subscriber can find it by
        // dereferencing the storage identifier it was given and nothing is hardcoded.
        String kid = com.ebremer.lws.notify.HttpMessageSignatures.keyId();
        JsonArrayBuilder verification = Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                        .add("id", cfg.storageRootUri() + "#" + kid)
                        .add("type", "JsonWebKey")
                        .add("controller", cfg.storageRootUri())
                        .add("publicKeyJwk",
                                com.ebremer.lws.notify.HttpMessageSignatures.publicJwk()));

        return doc(cfg.storageRootUri(), T_STORAGE)
                .add("capability", capabilities)
                .add("verificationMethod", verification)
                .add("authentication", Json.createArrayBuilder()
                        .add(cfg.storageRootUri() + "#" + kid))
                .add("service", services)
                .build();
    }

    /** Render a capability's {@code service[]} entry. */
    private static JsonObjectBuilder serviceEntry(CapabilityDescriptor.ServiceEntry se) {
        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("type", se.type())
                .add("serviceEndpoint", se.serviceEndpoint());
        if (se.conformsTo() != null && !se.conformsTo().isEmpty()) {
            JsonArrayBuilder ct = Json.createArrayBuilder();
            se.conformsTo().forEach(ct::add);
            b.add("conformsTo", ct);
        }
        if (se.note() != null) {
            b.add("note", se.note());
        }
        return b;
    }

    /** Render a capability's {@code capability[]} entry. */
    private static JsonObjectBuilder capabilityEntry(CapabilityDescriptor.CapabilityEntry ce) {
        JsonObjectBuilder b = Json.createObjectBuilder().add("type", ce.type());
        if (ce.serviceEndpoint() != null) {
            b.add("serviceEndpoint", ce.serviceEndpoint());
        }
        if (ce.note() != null) {
            b.add("note", ce.note());
        }
        return b;
    }

    /** One entry in a container's {@code items} array. */
    public record Item(
            String id,
            List<String> types,
            String mediaType,
            Long size,
            String modified) {
    }

    private static JsonObjectBuilder item(Item it) {
        JsonObjectBuilder b = Json.createObjectBuilder().add("id", it.id());

        // `type` is a plain string when there is exactly one, an array otherwise.
        // A resource is always at least DataResource or Container, and may carry
        // additional types discovered by the file readers.
        List<String> types = it.types();
        if (types.size() == 1) {
            b.add("type", types.get(0));
        } else {
            JsonArrayBuilder arr = Json.createArrayBuilder();
            types.forEach(arr::add);
            b.add("type", arr);
        }
        if (it.mediaType() != null) {
            b.add("mediaType", it.mediaType());
        }
        if (it.size() != null) {
            b.add("size", it.size());
        }
        if (it.modified() != null) {
            b.add("modified", it.modified());
        }
        return b;
    }

    /**
     * A container representation.
     *
     * <p>{@code totalItems} reflects the <em>full</em> membership the client is
     * allowed to see, not the size of this page — pagination changes {@code items}
     * only.
     */
    public static JsonObject container(String id, long totalItems, List<Item> items) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        items.forEach(i -> arr.add(item(i)));
        return doc(id, T_CONTAINER)
                .add("totalItems", totalItems)
                .add("items", arr)
                .build();
    }

    /**
     * A Type Search result set.
     *
     * <p>Synthetic by construction: it reuses the container media type and
     * pagination model for client convenience, but it identifies no container, has
     * no containment relationship to its members, and is not retrievable as a
     * resource. Hence no {@code id}.
     */
    public static JsonObject containerPage(long totalItems, List<Item> items) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        items.forEach(i -> arr.add(item(i)));
        return doc(null, T_CONTAINER_PAGE)
                .add("totalItems", totalItems)
                .add("items", arr)
                .build();
    }

    /**
     * A Type Index: the distinct types present in the storage that this client is
     * authorized to see. Each item carries only an {@code id}.
     */
    public static JsonObject typeIndex(long totalItems, List<String> typeUris) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        typeUris.forEach(t -> arr.add(Json.createObjectBuilder().add("id", t)));
        return doc(null, T_TYPE_INDEX)
                .add("totalItems", totalItems)
                .add("items", arr)
                .build();
    }

    public static String dataResourceType() {
        return T_DATA_RESOURCE;
    }

    public static String containerType() {
        return T_CONTAINER;
    }
}
