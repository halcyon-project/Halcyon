package com.ebremer.halcyon.server.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author erich
 */
public class PathMap {
    
    public static Optional<Path> http2file(URI uri) {
        String f = uri.toString();
        HalcyonSettings settings = HalcyonSettings.getSettings();
        String mainpath = settings.getHostName();
        if (f.startsWith(mainpath)) {
            String cut = f.substring(mainpath.length());
            HashMap<String, String> mappings = settings.getmappings();
            for (String key : mappings.keySet()) {
                if (cut.startsWith(key)) {
                    String chunk = cut.substring(key.length());
                    return Optional.of(Path.of(mappings.get(key), chunk));
                }
            }
        }
        return Optional.empty();
    }
    
    public static Optional<URI> file2http(String furi) {
        HalcyonSettings settings = HalcyonSettings.getSettings();
        HashMap<String, String> mappings = settings.getfile2httpMappings();
        for (String key : mappings.keySet()) {
            if (furi.startsWith(key)) {
                String chunk = furi.substring(key.length()+1);
                URI uri;
                try {
                    uri = new URI(settings.getHostName()+mappings.get(key)+chunk);
                    return Optional.of(uri);
                } catch (URISyntaxException ex) {
                    Logger.getLogger(PathMap.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return Optional.empty();
    }
}
