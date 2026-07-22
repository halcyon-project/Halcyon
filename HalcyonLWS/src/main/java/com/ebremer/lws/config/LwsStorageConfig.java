package com.ebremer.lws.config;

import java.nio.file.Path;
import java.util.List;

/**
 * One configured LWS storage.
 *
 * @param urlPath     the mount path, normalised to a leading slash and no
 *                    trailing slash (e.g. {@code /W3Clws})
 * @param contentRoot root of the sharded content store on disk
 * @param naming      how resource URIs are minted — the only axis on which the
 *                    two storages differ
 * @param siteUrl     the instance's site URL (Halcyon's {@code ProxyHostName}),
 *                    with no trailing slash
 * @param mounts      other physical disks backing sub-containers of a MIRROR
 *                    storage ({@link LwsMount}); always empty for the flat
 *                    storage, whose keys are not paths
 * @param backend     the storage's {@code :hasBackend} node from {@code settings.ttl},
 *                    or {@code null} for the built-in disk backends. Carried verbatim —
 *                    a {@link com.ebremer.lws.store.spi.ContentStoreProvider} recognises
 *                    its own node (typically by {@code rdf:type}) and parses its own
 *                    vocabulary from it; core never interprets it. For a remote backend
 *                    (e.g. S3), {@code contentRoot} is the LOCAL side: the materialization
 *                    cache root.
 */
public record LwsStorageConfig(
        String urlPath,
        Path contentRoot,
        NamingPolicyType naming,
        String siteUrl,
        List<LwsMount> mounts,
        org.apache.jena.rdf.model.Resource backend) {

    /** The common case: a storage on one disk. */
    public LwsStorageConfig(String urlPath, Path contentRoot, NamingPolicyType naming,
            String siteUrl) {
        this(urlPath, contentRoot, naming, siteUrl, List.of(), null);
    }

    /** A storage with mounts and no pluggable backend. */
    public LwsStorageConfig(String urlPath, Path contentRoot, NamingPolicyType naming,
            String siteUrl, List<LwsMount> mounts) {
        this(urlPath, contentRoot, naming, siteUrl, mounts, null);
    }

    /**
     * Service endpoints, reserved by their <em>leading dot</em>. The slug sanitiser strips
     * leading dots, so a client cannot mint a name that collides with one of these and no
     * escaping scheme is needed.
     */
    public static final String DESCRIPTION = ".description";
    public static final String TYPE_INDEX = ".types/index";
    public static final String TYPE_SEARCH = ".types/search";
    public static final String SUBSCRIPTIONS = ".notifications/subscriptions";

    /** The DataSharingService endpoints (lws-access-requests): ODRL access requests and grants. */
    public static final String ACCESS_REQUESTS = ".access/requests";
    public static final String ACCESS_GRANTS = ".access/grants";

    /**
     * The IIIF Image service endpoint. Unlike the endpoints above, it is <em>routed and advertised
     * by an {@code EndpointCapability}</em> the hosting application installs, not by this module.
     * The name is kept here — reserved like the others (a leading dot no client slug can mint) — as
     * the single source of truth for the path, shared by that capability and by other modules that
     * build IIIF URLs (the MCP tools, the image servlet's forward target).
     */
    public static final String IIIF = ".iiif";

    /**
     * Suffix of a resource's linkset (its metadata resource).
     *
     * <p>Reserved by its <em>trailing</em> form, which is a different problem from the
     * leading-dot names above and needs a different guard: stripping leading dots does
     * nothing to {@code report.meta}. {@code Slugs.sanitize} appends an underscore to any
     * name that would end in this, because a name that could would be minted, counted, and
     * then read back as the linkset of a primary resource that does not exist — leaving it
     * permanently unreachable.
     */
    public static final String LINKSET_SUFFIX = ".meta";

    /** Suffix of a resource's ACP access control resource. Reserved like {@link #LINKSET_SUFFIX}. */
    public static final String ACR_SUFFIX = ".acr";

    public LwsStorageConfig {
        if (urlPath == null || !urlPath.startsWith("/")) {
            throw new IllegalArgumentException("lws urlPath must start with '/': " + urlPath);
        }
        while (urlPath.length() > 1 && urlPath.endsWith("/")) {
            urlPath = urlPath.substring(0, urlPath.length() - 1);
        }
        while (siteUrl != null && siteUrl.endsWith("/")) {
            siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
        }
        mounts = mounts == null ? List.of() : List.copyOf(mounts);
        if (!mounts.isEmpty() && naming != NamingPolicyType.SLUG) {
            // A mount maps a KEY PREFIX to a disk, and only the mirror storage's
            // keys are paths — a UUID key has no prefix a mount could claim.
            throw new IllegalArgumentException(
                    "mounts require the slug (mirror) naming policy: " + urlPath);
        }
    }

    /** The base URI of this storage, e.g. {@code https://localhost:8888/W3Clws}. */
    public String baseUri() {
        return siteUrl + urlPath;
    }

    /**
     * The URI identifying the storage, and also its storage root container.
     *
     * <p>lws10-core allows the storage URI, the storage-description URI and the
     * storage root to be distinct or identical; here the storage and its root are
     * the same resource.
     *
     * <p>It ends in a slash in <em>both</em> storages, and that is deliberate. The
     * root sits exactly at the mount point, so a root of {@code /W3Clws} would put
     * its own auxiliary resources at {@code /W3Clws.meta} and {@code /W3Clws.acr} —
     * outside the {@code /W3Clws/*} servlet mapping, where Wicket would answer
     * instead. A trailing slash keeps them inside the mount, with no special case
     * anywhere else.
     *
     * <p>This does not give the flat storage slash semantics. Slash semantics is
     * about whether a <em>child's</em> URI nests inside its parent's, and in the
     * UUID storage it never does: every resource is {@code {base}/{uuid}}, however
     * deep it sits.
     */
    public String storageRootUri() {
        return baseUri() + "/";
    }

    /** The servlet mapping. {@code /W3Clws/*} also matches {@code /W3Clws} itself. */
    public String servletMapping() {
        return urlPath + "/*";
    }

    public String descriptionUri() {
        return baseUri() + "/" + DESCRIPTION;
    }

    public String typeIndexUri() {
        return baseUri() + "/" + TYPE_INDEX;
    }

    public String typeSearchUri() {
        return baseUri() + "/" + TYPE_SEARCH;
    }

    public String subscriptionsUri() {
        return baseUri() + "/" + SUBSCRIPTIONS;
    }

    public String accessRequestsUri() {
        return baseUri() + "/" + ACCESS_REQUESTS;
    }

    public String accessGrantsUri() {
        return baseUri() + "/" + ACCESS_GRANTS;
    }

    public String iiifUri() {
        return baseUri() + "/" + IIIF;
    }

    /**
     * The {@code realm} of the {@code WWW-Authenticate} challenge, and the
     * {@code aud} an access token must carry. lws10-core requires a client to
     * verify that the request URI is logically contained within the realm, so the
     * storage base URI is exactly the right value.
     */
    public String realm() {
        return baseUri();
    }
}
