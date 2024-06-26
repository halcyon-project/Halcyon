package com.ebremer.halcyon.server.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *
 * @author erich
 */
public class PathMapper {
    private static PathMapper pathmapper = null;
    private final List<PathMap> sortByHttp;
    private final List<PathMap> sortByFile;
    private final String hostname;
    
    private PathMapper() {
        HalcyonSettings settings = HalcyonSettings.getSettings();
        hostname = settings.getHostName();
        sortByHttp = settings.gethttp2fileMappings().entrySet().stream()
                      .sorted(Map.Entry.comparingByKey())
                      .map(e -> new PathMap(e.getKey(), e.getValue()))
                      .collect(Collectors.toCollection(ArrayList::new)).reversed();
        sortByFile = settings.gethttp2fileMappings().entrySet().stream()
                      .sorted(Map.Entry.comparingByValue())
                      .map(e -> new PathMap(e.getKey(), e.getValue()))
                      .collect(Collectors.toCollection(ArrayList::new)).reversed();
    }
    
    public Optional<URI> http2file(URI uri) {
        String f = uri.toString();
        if (f.startsWith(hostname)) {
            String cut = f.substring(hostname.length());
            for (PathMap pm : sortByHttp) {
                String key = pm.http();
                if (cut.startsWith(key)) {
                    String chunk = cut.substring(key.length());
                    Path wow = Path.of(pm.file().substring(1), chunk);
                    return Optional.of(wow.toUri());
                }
            }
        }
        return Optional.empty();
    }

    public Optional<URI> http2file(String f) {
        if (f.startsWith(hostname)) {
            String cut = f.substring(hostname.length());
            for (PathMap pm : sortByHttp) {
                String key = pm.http();
                if (cut.startsWith(key)) {
                    String chunk = cut.substring(key.length());
                    Path wow = Path.of(pm.file().substring(1), chunk);
                    return Optional.of(wow.toUri());
                }
            }
        }
        return Optional.empty();
    }
    
    public Optional<URI> file2http(String furi) {
        for (PathMap pathmap : sortByFile) {
            String key = pathmap.file();
            if (furi.startsWith(key)) {
                String chunk = furi.substring(key.length()+1);
                URI uri;
                try {
                    uri = new URI(hostname+pathmap.http()+chunk);
                    return Optional.of(uri);
                } catch (URISyntaxException ex) {
                    Logger.getLogger(PathMapper.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return Optional.empty();
    }
    
    public Optional<URI> file2http(URI furi) {
        return file2http(furi.getPath());
    }
    
    public static PathMapper getPathMapper() {
        if (pathmapper == null) {
            pathmapper = new PathMapper();            
        }
        return pathmapper;
    }
}
