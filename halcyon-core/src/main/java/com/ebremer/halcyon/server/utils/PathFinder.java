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
        for (StorageLocation sl : s.getStorageLocations()) {
            Path src = Path.of(uri);
            if (src.startsWith(sl.path)) {
                return sl.urlpath+src.toUri().toString().substring(sl.path.toUri().toString().length());
            } else { 
                System.out.println("NO MATCH : "+path);
            }
        }
        return null;
    }
    
    public static String LocalPath2IIIFURL(String path) {
        String host = HalcyonSettings.getSettings().getProxyHostName();
        return String.format("%s/iiif/?iiif=%s%s", host, host, PathFinder.Path2URL(path));
    }
    
    public static String LocalPath2IIIFInfoURL(String path) {
        return String.format("%s/info.json", PathFinder.LocalPath2IIIFURL(path));
    }
}
