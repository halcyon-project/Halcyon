package com.ebremer.halcyon.server;

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

    public static String[] getSecuredURLs() {
        String[] secured = {
           // "/users/*",
            //"/ldp/*",
            "/blank",
            "/skunkworks/yay",
            "/f*",
            "/callback",
            "/about",
            "/iiif*/",
            "/sparql",
            "/invalidateSession",
            "/revisionhistory",
            "/collections"};
        return secured;
    }

    public static String getWicketIgnores() {
        String[] src = {
            "/users",
            "/ldp",
            "/lws/",
            "/HalcyonStorage",
            "/raptor",
            "/invalidateSession",
            "/callback",
            "/h2",
            "/skunkworks/",
            "/login",
            "/auth",
            "/three.js/",
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
