package com.ebremer.halcyon.lib;

import java.io.File;
import java.net.URI;

/**
 *
 * @author erich
 */
public class URITools {

    public static String fix(String furi) {
        String f = furi;
        if (!f.startsWith("file://")) {
            f = "file:///"+ f.substring("file:/".length());
        }
        //System.out.println("FIX furi --> "+furi+" ---> "+f);
        return f;
    } 

    public static String fix(URI furi) {
        String f = furi.toString();
        if (!f.startsWith("file://")) {
            f = "file:///"+ f.substring("file:/".length());
        }
        return f;
    } 
    
    public static String fix(File furi) {
        String f = furi.toURI().toString();
        if (!f.startsWith("file://")) {
            f = "file:///"+ f.substring("file:/".length());
        }
        return f;
    }    
}
