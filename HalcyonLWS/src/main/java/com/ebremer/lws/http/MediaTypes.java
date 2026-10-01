package com.ebremer.lws.http;

/**
 * The media types the LWS protocol names.
 */
public final class MediaTypes {

    /** The LWS media type. Container representations MUST support it. */
    public static final String LWS_JSON = "application/lws+json";

    /**
     * The storage-description media type: a document that is a specialization of a W3C
     * Controlled Identifier document extended with the LWS vocabulary.
     *
     * <p>A request for the storage URI MUST be answered with this type unless content
     * negotiation requires another (lws10-core §Discovery and Binding). It was
     * {@code application/lws+json} until w3c/lws-protocol#183 made the description a CID
     * document; the description is still offered as {@code application/lws+json} and Turtle
     * through negotiation, which is the "other representations MAY be available" allowance.
     */
    public static final String LWS_CID = "application/lws+cid";

    /**
     * lws10-core requires content negotiation across these three for container
     * representations, and is explicit that "the response payload MUST be
     * identical regardless of the requested media type — only the
     * {@code Content-Type} response header varies". So they share one serializer;
     * the negotiation only decides what to label the bytes.
     */
    public static final String LD_JSON = "application/ld+json";
    public static final String JSON = "application/json";

    /** Considered equivalent to {@link #LWS_JSON} — LWS containers are a restricted JSON-LD profile. */
    public static final String LD_JSON_LWS_PROFILE =
            "application/ld+json; profile=\"https://www.w3.org/ns/lws/v1\"";

    /** Optional additional serialization offered through content negotiation. */
    public static final String TURTLE = "text/turtle";

    /** A resource's linkset (RFC 9264). */
    public static final String LINKSET_JSON = "application/linkset+json";

    /** The minimum patch format a server MUST support, for resources and linksets alike. */
    public static final String MERGE_PATCH_JSON = "application/merge-patch+json";

    /** JSON Patch (RFC 6902) — accepted on JSON data resources, in addition to merge patch. */
    public static final String JSON_PATCH = "application/json-patch+json";

    /** {@code Accept-Patch} for a JSON data resource: both patch formats it accepts. */
    public static final String ACCEPT_PATCH_JSON = MERGE_PATCH_JSON + ", " + JSON_PATCH;

    /**
     * The baseline Type Search filter format, carried in the body of an HTTP
     * {@code QUERY} request.
     *
     * <p>Introduced by w3c/lws-protocol#179, which replaced the published GET/POST
     * forms of the Type Search Service with {@code QUERY} (RFC 10008). Note it is
     * plain JSON, not JSON-LD: it has no {@code @context}, and any member whose
     * name begins with {@code @} MUST be ignored.
     */
    public static final String LWS_QUERY_JSON = "application/lws-query+json";

    /** RFC 9457 structured error bodies. */
    public static final String PROBLEM_JSON = "application/problem+json";

    public static final String OCTET_STREAM = "application/octet-stream";

    private MediaTypes() {
    }

    /**
     * True if {@code accept} admits an {@code application/lws+json} response.
     *
     * <p>An absent {@code Accept}, or one that admits any type, counts as admitting
     * it: the search-index spec is explicit that "a request that omits
     * {@code Accept} or admits that media type never results in 406".
     */
    public static boolean admitsLwsJson(String accept) {
        if (accept == null || accept.isBlank()) {
            return true;
        }
        String a = accept.toLowerCase();
        return a.contains("*/*")
                || a.contains("application/*")
                || a.contains(LWS_JSON)
                || a.contains(LD_JSON)
                || a.contains(JSON);
    }

    /**
     * True if an {@code Accept} header admits {@code type} — a request that specifically asks for
     * something else gets a 406. An absent {@code Accept}, or one carrying {@code *}&#47;{@code *},
     * the type's family ({@code application/*}), or the type itself, admits it.
     *
     * <p>Simplified in the same way as {@link #admitsLwsJson} — no q-value ranking — because these
     * resources have a single representation and the only question is whether the client will take
     * it. It is enough to reject a genuinely incompatible {@code Accept} without standing up a full
     * negotiator for an endpoint a client rarely exercises.
     */
    public static boolean admits(String accept, String type) {
        if (accept == null || accept.isBlank()) {
            return true;
        }
        String a = accept.toLowerCase();
        if (a.contains("*/*")) {
            return true;
        }
        int slash = type.indexOf('/');
        String family = slash > 0 ? type.substring(0, slash) + "/*" : type;
        return a.contains(family) || a.contains(type.toLowerCase());
    }

    /**
     * True if the request asks for the storage description rather than a container listing.
     *
     * <p>The storage URI and the storage root container are the same resource here, so a GET on
     * it has two honest answers. lws10-core settles which one is the default: "Requests for the
     * storage URI MUST return a document that conforms to the storage description resource data
     * model with a media type of {@code application/lws+cid}, unless content negotiation requires
     * a different format." So the description is the answer to no {@code Accept}, to a wildcard,
     * to a browser's default and to {@code application/lws+cid} itself; the container listing is
     * the answer only when the client names one of the container's own formats
     * ({@code application/lws+json}, {@code application/ld+json}, {@code application/json} or
     * {@code text/turtle}) and not the CID type. Every container client names its format, which is
     * how Halcyon's own {@code LwsClient} has always read the root.
     */
    public static boolean prefersStorageDescription(String accept) {
        if (accept == null || accept.isBlank()) {
            return true;
        }
        String a = accept.toLowerCase();
        if (a.contains(LWS_CID)) {
            return true;
        }
        return !(a.contains(LWS_JSON) || a.contains(LD_JSON) || a.contains(JSON) || a.contains(TURTLE));
    }

    /**
     * True if {@code accept} admits the storage description in one of the forms it is offered
     * in: {@code application/lws+cid}, the JSON family, or Turtle.
     */
    public static boolean admitsStorageDescription(String accept) {
        return admits(accept, LWS_CID) || admitsLwsJson(accept) || admits(accept, TURTLE);
    }

    /**
     * Pick the {@code Content-Type} for the storage description. {@code application/lws+cid} is
     * the default — the type lws10-core requires of a response to the storage URI — and the
     * JSON forms are offered to a client that asks for one by name.
     */
    public static String negotiateDescription(String accept) {
        if (accept == null || accept.isBlank()) {
            return LWS_CID;
        }
        String a = accept.toLowerCase();
        if (a.contains(LWS_CID)) {
            return LWS_CID;
        }
        if (a.contains(LWS_JSON)) {
            return LWS_JSON;
        }
        if (a.contains(LD_JSON)) {
            return LD_JSON;
        }
        if (a.contains(JSON)) {
            return JSON;
        }
        return LWS_CID;
    }

    /**
     * Pick the {@code Content-Type} to label an LWS document with, given the
     * request's {@code Accept}. The body is the same either way.
     */
    public static String negotiate(String accept) {
        if (accept == null || accept.isBlank()) {
            return LWS_JSON;
        }
        String a = accept.toLowerCase();
        // Most specific first: a client asking for lws+json must get lws+json back.
        if (a.contains(LWS_JSON)) {
            return LWS_JSON;
        }
        if (a.contains(LD_JSON)) {
            return LD_JSON;
        }
        if (a.contains(JSON)) {
            return JSON;
        }
        return LWS_JSON;
    }

    /**
     * True if a media type denotes a JSON document — {@code application/json} itself, or
     * anything carrying the {@code +json} structured syntax suffix of RFC 6839:
     * {@code application/ld+json}, {@code application/lws+json}, {@code application/geo+json}.
     *
     * <p>This is what decides whether a resource's content can be merge-patched at all. A
     * JSON Merge Patch is defined by recursing into the target document's object tree, so
     * there has to <em>be</em> an object tree: there is nothing in a TIFF for a patch to
     * merge into.
     */
    public static boolean isJson(String mediaType) {
        String m = bare(mediaType);
        return m != null && (JSON.equals(m) || m.endsWith("+json"));
    }

    /**
     * True if a media type is <em>actively scriptable</em> when a browser navigates to it:
     * HTML, and the XML family (XHTML, SVG, and raw XML — all of which can carry script,
     * event handlers, or an {@code xml-stylesheet} that executes in the document's origin).
     *
     * <p>These are the types the storage must never serve executable from its own origin:
     * any agent granted write access could otherwise hand every later reader a stored XSS
     * running as the storage's own site. The serving path answers them with
     * {@code Content-Security-Policy: sandbox} — the document still renders when opened or
     * embedded, but as a unique opaque origin with no script.
     */
    public static boolean scriptable(String mediaType) {
        String m = bare(mediaType);
        if (m == null) {
            return false;
        }
        return m.equals("text/html")
                || m.equals("text/xml")
                || m.equals("application/xml")
                || m.endsWith("+xml");
    }

    /** Strip any parameters: {@code application/json; charset=utf-8} -> {@code application/json}. */
    public static String bare(String contentType) {
        if (contentType == null) {
            return null;
        }
        int semi = contentType.indexOf(';');
        return (semi < 0 ? contentType : contentType.substring(0, semi)).trim().toLowerCase();
    }
}
