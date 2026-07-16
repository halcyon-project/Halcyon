package com.ebremer.lws.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * W3C Linked Web Storage vocabulary.
 *
 * <p>This is a faithful, complete rendering of the terms defined by the LWS
 * JSON-LD context and by lws10-core / lws10-notifications / lws10-searchindex.
 *
 * <p>It deliberately does not reuse {@code com.ebremer.ns.LWS} in halcyon-core.
 * That class carries Halcyon-flavoured terms ({@code contains}, {@code partOf},
 * {@code tag}) that are <em>not</em> in the W3C context, and belongs to the
 * legacy {@code /lws/**} servlet, which this module must not disturb. Both
 * classes mint terms in the same namespace, so the RDF they produce interoperates
 * at the URI level regardless.
 *
 * @see <a href="https://w3c.github.io/lws-protocol/lws10-core/">LWS Protocol 1.0</a>
 */
public final class LWS {

    public static final String NS = "https://www.w3.org/ns/lws#";

    /**
     * The normative JSON-LD context value. Container and storage-description
     * representations MUST carry this.
     *
     * <p>Emit it, never dereference it: as of this writing the URI is not yet
     * published and returns 404. Nothing in this module feeds LWS documents to a
     * JSON-LD processor, so no runtime fetch is ever attempted.
     */
    public static final String CONTEXT = "https://www.w3.org/ns/lws/v1";

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

    public static final Resource Storage = cls("Storage");
    public static final Resource Container = cls("Container");
    public static final Resource ContainerPage = cls("ContainerPage");
    public static final Resource DataResource = cls("DataResource");
    public static final Resource MetadataResource = cls("MetadataResource");

    // --- Service types (storage description `service` array) ---------------

    public static final Resource StorageDescription = cls("StorageDescription");
    public static final Resource NotificationService = cls("NotificationService");
    public static final Resource TypeIndexService = cls("TypeIndexService");
    public static final Resource TypeSearchService = cls("TypeSearchService");
    public static final Resource DataSharingService = cls("DataSharingService");

    // --- Search index (lws10-searchindex) ----------------------------------

    public static final Resource TypeIndex = cls("TypeIndex");

    // --- Notifications (lws10-notifications) -------------------------------

    public static final Resource Notification = cls("Notification");
    public static final Resource WebhookSubscription = cls("WebhookSubscription");

    // --- Properties --------------------------------------------------------

    /** The members of a container. Server-managed; clients cannot set it directly. */
    public static final Property items = prop("items");

    public static final Property capability = prop("capability");
    public static final Property service = prop("service");
    public static final Property serviceEndpoint = prop("serviceEndpoint");

    public static final Property subscriptionType = prop("subscriptionType");
    public static final Property subscription = prop("subscription");
    public static final Property activity = prop("activity");
    public static final Property topic = prop("topic");
    public static final Property storage = prop("storage");

    // --- Link relations ----------------------------------------------------

    /**
     * The {@code rel} value for the storage description link. lws10-core requires
     * the fully-qualified URI here, not a short token:
     * {@code Link: <...>; rel="https://www.w3.org/ns/lws#storageDescription"}.
     */
    public static final String REL_STORAGE_DESCRIPTION = NS + "storageDescription";

    /** {@code Prefer} header URI for selecting which link relations are returned. */
    public static final String PREFER_LINK_RELATIONS = NS + "PreferLinkRelations";

    private LWS() {
    }
}
