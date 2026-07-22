package com.ebremer.lws.http;

import com.ebremer.lws.config.LwsStorageConfig;
import jakarta.servlet.http.HttpServletRequest;

/**
 * What a request URI refers to inside a storage.
 *
 * <p>The canonical URI is always rebuilt from the configured site URL, never from
 * the {@code Host} header, so a resource's identity does not change with the route
 * a request happened to take (proxy, direct, IP literal). Resource URIs are stable
 * identifiers that get stored in RDF and handed to other agents; they cannot be a
 * function of the inbound request.
 */
public record Target(Kind kind, String uri, String subId) {

    public enum Kind {
        /** The storage root container, which is also the storage itself. */
        STORAGE_ROOT,
        /** The storage description resource. */
        DESCRIPTION,
        /** The Type Index service endpoint. */
        TYPE_INDEX,
        /** The Type Search service endpoint. Answers {@code QUERY}. */
        TYPE_SEARCH,
        /** The notification subscriptions collection. */
        SUBSCRIPTIONS,
        /** An individual subscription. */
        SUBSCRIPTION,
        /** The access-request collection (DataSharingService). */
        ACCESS_REQUESTS,
        /** An individual access request. {@link #subId} is its id. */
        ACCESS_REQUEST,
        /** The access-grant collection (DataSharingService). */
        ACCESS_GRANTS,
        /** An individual access grant. {@link #subId} is its id. */
        ACCESS_GRANT,
        /** A resource's linkset (RFC 9264). {@link #uri} is the resource it describes. */
        LINKSET,
        /** A resource's ACP access control resource. {@link #uri} is the resource it controls. */
        ACR,
        /** An ordinary container or data resource. */
        RESOURCE
    }

    /**
     * Resolve the request.
     *
     * <p>{@code getPathInfo()} is used rather than {@code getRequestURI()} because
     * it is already percent-decoded and already has the servlet path stripped. An
     * encoded slash cannot smuggle a path segment through it: Jetty rejects
     * {@code %2F} in paths by default.
     */
    public static Target resolve(LwsStorageConfig cfg, HttpServletRequest req) {
        String path = req.getPathInfo();

        // Mapped as /W3Clws/*, so /W3Clws itself arrives with a null pathInfo and
        // /W3Clws/ with "/". Both are the storage root.
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return new Target(Kind.STORAGE_ROOT, cfg.storageRootUri(), null);
        }

        String rel = path.startsWith("/") ? path.substring(1) : path;

        // Reserved service endpoints. The slug sanitiser strips leading dots, so a
        // client can never create a resource whose name collides with one of these.
        switch (rel) {
            case LwsStorageConfig.DESCRIPTION ->
                    { return new Target(Kind.DESCRIPTION, cfg.descriptionUri(), null); }
            case LwsStorageConfig.TYPE_INDEX ->
                    { return new Target(Kind.TYPE_INDEX, cfg.typeIndexUri(), null); }
            case LwsStorageConfig.TYPE_SEARCH ->
                    { return new Target(Kind.TYPE_SEARCH, cfg.typeSearchUri(), null); }
            case LwsStorageConfig.SUBSCRIPTIONS ->
                    { return new Target(Kind.SUBSCRIPTIONS, cfg.subscriptionsUri(), null); }
            case LwsStorageConfig.ACCESS_REQUESTS ->
                    { return new Target(Kind.ACCESS_REQUESTS, cfg.accessRequestsUri(), null); }
            case LwsStorageConfig.ACCESS_GRANTS ->
                    { return new Target(Kind.ACCESS_GRANTS, cfg.accessGrantsUri(), null); }
            default -> { }
        }

        if (rel.startsWith(LwsStorageConfig.SUBSCRIPTIONS + "/")) {
            String id = rel.substring(LwsStorageConfig.SUBSCRIPTIONS.length() + 1);
            if (!id.isEmpty() && !id.contains("/")) {
                return new Target(Kind.SUBSCRIPTION, cfg.subscriptionsUri() + "/" + id, id);
            }
        }
        if (rel.startsWith(LwsStorageConfig.ACCESS_REQUESTS + "/")) {
            String id = rel.substring(LwsStorageConfig.ACCESS_REQUESTS.length() + 1);
            if (!id.isEmpty() && !id.contains("/")) {
                return new Target(Kind.ACCESS_REQUEST, cfg.accessRequestsUri() + "/" + id, id);
            }
        }
        if (rel.startsWith(LwsStorageConfig.ACCESS_GRANTS + "/")) {
            String id = rel.substring(LwsStorageConfig.ACCESS_GRANTS.length() + 1);
            if (!id.isEmpty() && !id.contains("/")) {
                return new Target(Kind.ACCESS_GRANT, cfg.accessGrantsUri() + "/" + id, id);
            }
        }

        String uri = cfg.baseUri() + "/" + rel;

        // Auxiliary resources hang off their primary resource by suffix. Their
        // lifetime is bound to it, and they are discovered through Link headers
        // rather than by a client constructing these names.
        if (rel.endsWith(LwsStorageConfig.LINKSET_SUFFIX)) {
            String primary = uri.substring(0, uri.length() - LwsStorageConfig.LINKSET_SUFFIX.length());
            return new Target(Kind.LINKSET, primary, null);
        }
        if (rel.endsWith(LwsStorageConfig.ACR_SUFFIX)) {
            String primary = uri.substring(0, uri.length() - LwsStorageConfig.ACR_SUFFIX.length());
            return new Target(Kind.ACR, primary, null);
        }

        return new Target(Kind.RESOURCE, uri, null);
    }

    /** The linkset URI of the resource this target names. */
    public String linksetUri() {
        return uri + LwsStorageConfig.LINKSET_SUFFIX;
    }

    /** The access control resource URI of the resource this target names. */
    public String acrUri() {
        return uri + LwsStorageConfig.ACR_SUFFIX;
    }
}
