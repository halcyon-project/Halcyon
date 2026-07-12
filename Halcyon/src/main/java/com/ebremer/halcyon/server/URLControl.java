package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
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
        return String.join(",", ignores);
    }
}
