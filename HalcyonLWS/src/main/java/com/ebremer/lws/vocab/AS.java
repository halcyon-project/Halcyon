package com.ebremer.lws.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Activity Streams 2.0 — the terms LWS borrows.
 *
 * <p>One role, since the LWS drafts of 2026-09-21 (w3c/lws-protocol#219): lws10-core carries
 * change events as Activity Streams activities, and a server MUST support {@link #Create},
 * {@link #Update} and {@link #Delete}. The container-representation terms this namespace used
 * to supply were moved by that change — {@code mediaType} became {@code dcterms:format},
 * {@code modified} became {@code dcterms:modified}, and {@code totalItems} became
 * {@code lws:totalItems}. See {@link Terms} and {@link LWS#totalItems}.
 */
public final class AS {

    public static final String NS = "https://www.w3.org/ns/activitystreams#";

    /** The AS 2.0 context, used alongside the LWS context in notification envelopes. */
    public static final String CONTEXT = "https://www.w3.org/ns/activitystreams";

    public static String getURI() {
        return NS;
    }

    private static Resource cls(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    private static Property prop(String local) {
        return ResourceFactory.createProperty(NS + local);
    }

    // --- Activity types a server MUST support -------------------------------

    public static final Resource Create = cls("Create");
    public static final Resource Update = cls("Update");
    public static final Resource Delete = cls("Delete");

    // --- Activity properties ------------------------------------------------

    public static final Property object = prop("object");
    public static final Property actor = prop("actor");
    public static final Property target = prop("target");
    public static final Property origin = prop("origin");
    public static final Property published = prop("published");

    private AS() {
    }
}
