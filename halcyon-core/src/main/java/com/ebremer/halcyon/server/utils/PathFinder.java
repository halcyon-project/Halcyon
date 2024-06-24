package com.ebremer.halcyon.server.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author erich
 */
public class PathFinder {
    public static String Path2URL(String path) {
        URI uri = null;
        try {
            uri = new URI(path);
        } catch (URISyntaxException ex) {
            Logger.getLogger(PathFinder.class.getName()).log(Level.SEVERE, null, ex);
        }
        HalcyonSettings s = HalcyonSettings.getSettings();
        for (ResourceHandler sl : s.GetResourceHandlers()) {
            Path src = Path.of(uri);
            if (src.startsWith(sl.urlPath())) {
                return sl.urlPath()+src.toUri().toString().substring(sl.resourceBase().getPath().length());
            } else { 
                System.out.println("NO MATCH : "+path);
            }
        }
        return null;
    }
    
    public static String LocalPath2IIIFURL(String path) {
        String host = HalcyonSettings.getSettings().getProxyHostName();
        //return String.format("%s/iiif/?iiif=%s%s", host, host, PathFinder.Path2URL(path));
        return String.format("%s/iiif/?iiif=%s", host, path);
    }
    
    public static String LocalPath2IIIFInfoURL(String path) {
        return String.format("%s/info.json", PathFinder.LocalPath2IIIFURL(path));
    }
}
