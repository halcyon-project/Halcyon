package com.ebremer.lws.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Activity Streams 2.0 — the terms LWS borrows.
 *
 * <p>Two distinct roles:
 * <ul>
 *   <li>The LWS JSON-LD context maps {@code totalItems}, {@code mediaType} and
 *       {@code modified} into this namespace, so container representations depend
 *       on it.</li>
 *   <li>lws10-notifications carries change events as AS activities; a server MUST
 *       support {@link #Create}, {@link #Update} and {@link #Delete}.</li>
 * </ul>
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

    // --- Terms the LWS context maps into this namespace ---------------------

    /** JSON-LD term {@code totalItems}. */
    public static final Property totalItems = prop("totalItems");

    /** JSON-LD term {@code mediaType}. */
    public static final Property mediaType = prop("mediaType");

    /** JSON-LD term {@code modified}. Note the term and the property differ. */
    public static final Property updated = prop("updated");

    private AS() {
    }
}
