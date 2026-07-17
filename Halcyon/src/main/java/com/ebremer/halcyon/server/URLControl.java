package com.ebremer.halcyon.server;

import com.ebremer.halcyon.gui.PageAccess;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.config.LwsSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
            "/f*",
            "/callback",
            "/iiif*/",
            "/invalidateSession"
        ));
        secured.addAll(PageAccess.securedPaths());
        return secured.toArray(String[]::new);
    }

    /** Mounted paths that additionally require the {@code admin} group (H4). */
    public static String[] getAdminURLs() {
        return PageAccess.adminPaths().toArray(String[]::new);
    }

    public static String getWicketIgnores() {
        String[] src = {
            "/users",
            "/ldp",
            "/lws/",
            "/HalcyonStorage",
            "/savestack",
            "/invalidateSession",
            "/callback",
            "/h2",
            "/skunkworks/",
            "/login",
            "/auth",
            "/three.js/",
            // L18: Graph3D's vendored libraries — Wicket must not try to route these.
            "/graph3d/",
            "/multi-viewer/",
            "/iiif/",
            "/halcyon/",
            "/images/",
            "/favicon.ico",
            "/rdf",
            "/talon/",
            "/threejs/",
            "/rdf/",
            "/zephyr/",
            "/rdflib/"
        };
        // The LWS data servlets (annotation save/fetch, LDP resources) are
        // mounted from the settings file's resource handlers; the Wicket
        // filter must never claim those URL trees, or their GET/PUT requests
        // render the home page (HTTP 200 HTML) instead of reaching the
        // servlet — data appears to save but never lands, and reads return
        // unparseable HTML. Append the configured paths so a settings change
        // (e.g. the historic /ldp -> /lws rename) cannot re-break this.
        List<String> ignores = new ArrayList<>(Arrays.asList(src));
        HalcyonSettings.getSettings().GetResourceHandlers().forEach(rh -> {
            if (!ignores.contains(rh.urlPath())) {
                ignores.add(rh.urlPath());
            }
        });
        // Same hazard, same fix, for the W3C Linked Web Storage mounts
        // (:hasLWSStorage). These are served by the HalcyonLWS module and are
        // unrelated to the resource handlers above, but the Wicket filter is
        // mapped on /* and would claim them just the same.
        LwsSettings.get().storages().forEach(st -> {
            if (!ignores.contains(st.urlPath())) {
                ignores.add(st.urlPath());
            }
        });
        return String.join(",", ignores);
    }
}
