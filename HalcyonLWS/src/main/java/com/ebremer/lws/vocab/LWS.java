package com.ebremer.lws.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * W3C Linked Web Storage vocabulary.
 *
 * <p>This is a faithful rendering of the terms defined by {@code lws10-vocab} — the
 * machine-readable vocabulary the LWS JSON-LD context is generated from — together with
 * the terms lws10-core / lws10-notifications-webhook / lws10-index name in their prose.
 *
 * <p>Note that several JSON-LD terms of the LWS context do <em>not</em> live in this
 * namespace: {@code format} is {@code dcterms:format}, {@code modified} is
 * {@code dcterms:modified}, {@code size} is {@code schema:size}, {@code inbox} is
 * {@code ldp:inbox}, {@code expires} is {@code schema:expires}, {@code conformsTo} is
 * {@code dcterms:conformsTo}, and the activity and ODRL terms belong to Activity Streams
 * and ODRL. They are mapped in {@link com.ebremer.lws.json.LwsRdf}, which is where this
 * module makes the context explicit; only the terms lws10-vocab actually mints are here.
 *
 * <p>It deliberately does not reuse {@code com.ebremer.ns.LWS} in halcyon-core. That class
 * carries Halcyon-flavoured terms ({@code contains}, {@code partOf}, {@code tag}) that are
 * <em>not</em> in the W3C context, and belongs to the legacy {@code /lws/**} servlet, which
 * this module must not disturb. Both classes mint terms in the same namespace, so the RDF
 * they produce interoperates at the URI level regardless.
 *
 * @see <a href="https://w3c.github.io/lws-protocol/lws10-core/">LWS Protocol 1.0</a>
 * @see <a href="https://w3c.github.io/lws-protocol/lws10-vocab/">LWS Vocabulary</a>
 */
public final class LWS {

    public static final String NS = "https://www.w3.org/ns/lws#";

    /**
     * The normative JSON-LD context value. Container representations MUST carry this; a
     * storage description MUST carry it second, after the CID context ({@link #CID_CONTEXT}).
     *
     * <p>Emit it, never dereference it: as of this writing the URI is not yet published and
     * returns 404. Nothing in this module feeds LWS documents to a JSON-LD processor, so no
     * runtime fetch is ever attempted. (lws10-core §JSON-LD Context advises against runtime
     * context fetches in any case.)
     */
    public static final String CONTEXT = "https://www.w3.org/ns/lws/v1";

    /**
     * The W3C Controlled Identifiers 1.0 context. A storage description resource is a
     * specialization of a controlled identifier document, so its {@code @context} MUST be an
     * array beginning with this URI followed by {@link #CONTEXT}.
     */
    public static final String CID_CONTEXT = "https://www.w3.org/ns/cid/v1";

    public static String getURI() {
        return NS;
    }

    private static Resource cls(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    private static Property prop(String local) {
        return ResourceFactory.createProperty(NS + local);
    }

    // --- Classes -----------------------------------------------------------

    /**
     * An HTTP resource that supports the LWS read operations — the common supertype of
     * {@link #Container} and {@link #DataResource}, and the widest of the three target
     * matchers an access grant may name.
     */
    public static final Resource StorageResource = cls("StorageResource");

    public static final Resource Storage = cls("Storage");
    public static final Resource Container = cls("Container");
    public static final Resource ContainerPage = cls("ContainerPage");
    public static final Resource DataResource = cls("DataResource");

    // --- Service types (storage description `service` array) ---------------

    /** REQUIRED in every storage description: its {@code serviceEndpoint} is the storage root. */
    public static final Resource StorageRoot = cls("StorageRoot");

    public static final Resource NotificationService = cls("NotificationService");
    public static final Resource TypeIndexService = cls("TypeIndexService");
    public static final Resource TypeSearchService = cls("TypeSearchService");
    public static final Resource DataSharingService = cls("DataSharingService");
    public static final Resource AccessRequestService = cls("AccessRequestService");
    public static final Resource AccessGrantService = cls("AccessGrantService");

    /** An OpenID Connect provider, as named by a subject's controlled identifier document. */
    public static final Resource OpenIdProvider = cls("OpenIdProvider");

    // --- Search and type index (lws10-index) -------------------------------

    public static final Resource TypeIndex = cls("TypeIndex");

    // --- Notifications -----------------------------------------------------

    public static final Resource Notification = cls("Notification");
    public static final Resource WebhookSubscription = cls("WebhookSubscription");

    // --- Access requests and grants ----------------------------------------

    public static final Resource AccessProfile = cls("AccessProfile");
    public static final Resource AccessPolicy = cls("AccessPolicy");
    public static final Resource AccessRequest = cls("AccessRequest");
    public static final Resource AccessGrant = cls("AccessGrant");

    // --- Properties --------------------------------------------------------

    /** The members of a container. Server-managed; clients cannot set it directly. */
    public static final Property items = prop("items");

    /** The number of members the client is allowed to see. Note: {@code lws:}, not {@code as:}. */
    public static final Property totalItems = prop("totalItems");

    public static final Property capability = prop("capability");
    public static final Property service = prop("service");
    public static final Property serviceEndpoint = prop("serviceEndpoint");

    public static final Property subscriptionType = prop("subscriptionType");
    public static final Property subscription = prop("subscription");
    public static final Property activity = prop("activity");
    public static final Property topic = prop("topic");
    public static final Property storage = prop("storage");

    /** The access policies of an access request or grant. */
    public static final Property access = prop("access");

    // --- Individuals -------------------------------------------------------

    /** The ODRL action for creating a resource within a container (ODRL defines no {@code create}). */
    public static final Resource create = cls("create");

    /** The ODRL {@code leftOperand} naming the client identifier of an HTTP request. */
    public static final Resource client = cls("client");

    // --- Link relations ----------------------------------------------------

    /**
     * The {@code rel} of the storage link, whose target is the canonical URI of the storage.
     * lws10-core requires the fully-qualified URI here, not a short token, and requires the
     * link on every GET/HEAD response targeting a storage resource — and SHOULD on a 401, so
     * a client can find the storage without hardcoding a URI:
     * {@code Link: <https://storage.example/>; rel="https://www.w3.org/ns/lws#storage"}.
     */
    public static final String REL_STORAGE = NS + "storage";

    /** {@code Prefer} header URI for selecting which link relations are returned. */
    public static final String PREFER_LINK_RELATIONS = NS + "PreferLinkRelations";

    /**
     * The access profile this storage advertises on its sharing services: the ODRL-based
     * profile lws10-core defines for access requests and grants.
     */
    public static final String ACCESS_PROFILE = NS + "AccessProfile";

    private LWS() {
    }
}
