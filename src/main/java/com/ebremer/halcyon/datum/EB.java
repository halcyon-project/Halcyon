package com.ebremer.halcyon.datum;

import java.io.File;
import java.net.URI;

/**
 *
 * @author erich
 */
public class EB {

    public static String fix(String furi) {
        String f = furi;
        if (!f.startsWith("file://")) {
            f = "file:///"+ f.substring("file:/".length());
        }
        //if (f.endsWith(".zip")) {
        //    f = f + "/";
       // }
        return f;
    } 

    public static String fix(URI furi) {
        String f = furi.toString();
        if (!f.startsWith("file://")) {
            f = "file:///"+ f.substring("file:/".length());
        }
        //if (f.endsWith(".zip")) {
          //  f = f + "/";
        //}
        return f;
    } 
    
    public static String fix(File furi) {
        String f = furi.toURI().toString();
        if (!f.startsWith("file://")) {
            f = "file:///"+ f.substring("file:/".length());
        }
        //if (f.endsWith(".zip")) {
          //  f = f + "/";
        //}
        return f;
    }    
}
