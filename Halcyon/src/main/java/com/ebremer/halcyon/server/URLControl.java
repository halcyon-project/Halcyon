package com.ebremer.halcyon.server;

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
        return String.join(",", src);
    }
}
