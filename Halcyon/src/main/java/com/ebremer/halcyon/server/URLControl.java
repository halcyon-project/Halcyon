package com.ebremer.halcyon.server;

import com.ebremer.halcyon.gui.PageAccess;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.oauth.AuthorizationServerSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author erich
 */
public class URLControl {

    /**
     * URL patterns the pac4j security filter guards (H4).
     * <p>
     * The page half is now DERIVED from {@link PageAccess} — the same table
     * {@code HalcyonApplication} mounts from — so the two cannot drift apart
     * again. They had: this list guarded {@code /collections} while the page was
     * mounted at {@code /containers}, and {@code /admin}, {@code /upload},
     * {@code /viewer}, {@code /stacks}, {@code /ListImages}, {@code /threed} and
     * {@code /user/account} appeared nowhere, so they were reachable unauthenticated.
     * <p>
     * Only the non-page (servlet) entries are still listed by hand below.
     */
    public static String[] getSecuredURLs() {
        List<String> secured = new ArrayList<>(Arrays.asList(
           // "/users/*",
            //"/ldp/*",
            "/skunkworks/yay",
            "/callback",
            // Prefix specs, not exact ones. "/iiif*/" was not a legal servlet mapping at all --
            // a glob may only sit at the end of a prefix spec -- and a filter pattern, unlike a
            // servlet mapping, is never parsed, so Jetty accepted it silently and matched it
            // against the literal seven characters. The IIIF servlet at "/iiif/*" was therefore
            // unguarded with no error anywhere. "/invalidateSession" was legal but exact, and so
            // covered nothing under the "/invalidateSession/*" the servlet is mounted on. A dead
            // "/f*" -- an exact spec containing a glob, matching nothing, guarding nothing -- is
            // gone. UrlMappingAudit now fails startup rather than let any of this recur silently.
            "/iiif/*",
            "/invalidateSession/*"
        ));
        secured.addAll(PageAccess.securedPaths());
        return secured.toArray(String[]::new);
    }

    /** Mounted paths that additionally require the {@code admin} group (H4). */
    public static String[] getAdminURLs() {
        return PageAccess.adminPaths().toArray(String[]::new);
    }

    public static String getWicketIgnores() {
        return wicketIgnores(mountPaths());
    }

    /** {@link #getWicketIgnores()} for the given mount paths (each without a trailing slash). */
    static String wicketIgnores(List<String> mounts) {
        String[] src = {
            "/users",
            "/ldp",
            "/lws/",
            "/HalcyonStorage",
            "/savestack",
            // Zephyr's annotation-layer relay servlet (session-authenticated).
            "/annotationlayer",
            // The storage browsers' open-in-a-tab relay servlet.
            LwsOpenServlet.PATH,
            "/invalidateSession",
            "/callback",
            "/h2",
            "/skunkworks/",
            "/login",
            // Interactive CID URL login (Option B): the entry point and the OP's redirect target,
            // under their names and their pre-rename aliases. Anonymous servlets (see Main.java),
            // so Wicket must not claim their URL trees.
            "/cid-login",
            "/cid-callback",
            "/webid-login",
            "/webid-callback",
            "/auth",
            "/three.js/",
            // L18: Graph3D's vendored libraries — Wicket must not try to route these.
            "/graph3d/",
            "/multi-viewer/",
            "/iiif/",
            "/halcyon/",
            "/images/",
            "/favicon.ico",
            "/talon/",
            "/threejs/",
            // /rdf2 (LWS SPARQL). Listed explicitly (the classic /rdf endpoint that
            // used to prefix-cover it has been removed) so the Wicket filter never
            // claims the LWS endpoint's URL tree.
            "/rdf2",
            "/rdf2/",
            // The palette's color-classes relay servlet (session-authenticated).
            "/colorclasses",
            "/zephyr/",
            "/rdflib/",
            // The MCP endpoint (module HalcyonMCP, bearer-authenticated by its
            // own filter) and the RFC 9728 metadata under /.well-known/ — both
            // are Spring MVC routes; if the Wicket filter claims them the
            // protocol answers with rendered HTML instead of JSON-RPC.
            "/mcp",
            // The embedded LWS authorization server's token endpoint and jwks_uri (its RFC 8414
            // metadata is under /.well-known/, below). Anonymous servlets mounted by
            // LwsStorageConfiguration; if the Wicket filter claimed them, a token request would get
            // the home page and every client would fail to authenticate with HTTP 200.
            //
            // Taken from the constant the servlets are mounted on rather than spelled again here:
            // a second spelling is what lets the two drift, and the failure would be silent.
            AuthorizationServerSettings.BASE_PATH + "/",
            "/.well-known/"
        };
        // The LWS data servlets (annotation save/fetch, LDP resources) are
        // mounted from the settings file's resource handlers; the Wicket
        // filter must never claim those URL trees, or their GET/PUT requests
        // render the home page (HTTP 200 HTML) instead of reaching the
        // servlet — data appears to save but never lands, and reads return
        // unparseable HTML. Append the configured paths so a settings change
        // (e.g. the historic /ldp -> /lws rename) cannot re-break this.
        List<String> ignores = new ArrayList<>(Arrays.asList(src));
        // Wicket matches an ignore entry as a plain string prefix, so a mount is listed as its
        // tree ("/lws/"), never as the bare path: "/lws" would also swallow the Wicket page
        // /lwscontainers (and anything else that merely starts with those characters), which
        // then fell through to a 404. The bare path itself — the mount with no trailing slash —
        // is kept from Wicket by getWicketIgnoredExactPaths(), which the filter checks exactly.
        mounts.forEach(path -> {
            String tree = path + "/";
            if (!ignores.contains(tree)) {
                ignores.add(tree);
            }
        });
        return String.join(",", ignores);
    }

    /**
     * The mount paths, with no trailing slash, that the Wicket filter must hand on untouched when
     * a request names one exactly (e.g. {@code /lws}). Their trees ({@code /lws/...}) are covered
     * by {@link #getWicketIgnores()}; the two together claim the mount and nothing beside it.
     */
    public static Set<String> getWicketIgnoredExactPaths() {
        return new LinkedHashSet<>(mountPaths());
    }

    /**
     * The URL paths of the configured data mounts, each without a trailing slash: the settings
     * file's resource handlers (annotation save/fetch, LDP resources) and the W3C Linked Web
     * Storage mounts (:hasLWSStorage, served by the HalcyonLWS module). The Wicket filter is
     * mapped on /* and must never claim these trees, or their GET/PUT requests render the home
     * page (HTTP 200 HTML) instead of reaching the servlet — data appears to save but never
     * lands, and reads return unparseable HTML. Read from the settings so a change there (e.g.
     * the historic /ldp -> /lws rename) cannot re-break this.
     */
    private static List<String> mountPaths() {
        List<String> paths = new ArrayList<>();
        HalcyonSettings.getSettings().GetResourceHandlers().forEach(rh -> paths.add(rh.urlPath()));
        LwsSettings.get().storages().forEach(st -> paths.add(st.urlPath()));
        return paths.stream()
                .filter(p -> p != null && !p.isBlank())
                .map(URLControl::withoutTrailingSlash)
                .filter(p -> !p.isEmpty())
                .distinct()
                .toList();
    }

    private static String withoutTrailingSlash(String path) {
        String p = path.trim();
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
