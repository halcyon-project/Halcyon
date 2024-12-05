package com.ebremer.halcyon.server.utils;

import com.ebremer.halcyon.lib.OperatingSystemInfo;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class PathMapper {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(PathMapper.class);
    private static PathMapper pathmapper = null;
    private final List<PathMap> sortByHttp;
    private final List<PathMap> sortByFile;
    private final String hostname;
    
    private PathMapper(HalcyonSettings settings) {        
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
        logger.trace("http2fileU: {}", uri);
        String f = uri.toString();
        if (f.startsWith(hostname)) {
            String cut = f.substring(hostname.length());
            logger.trace("http2fileU/cut: {}", cut);
            for (PathMap pm : sortByHttp) {
                String key = pm.http();
                logger.trace("http2fileU/key: {}", key);
                if (cut.startsWith(key)) {
                    String chunk = cut.substring(key.length());
                    logger.trace("http2fileU/chunk: {}", chunk);
                    Path wow = Path.of(pm.file(), chunk);
                    logger.trace("http2fileU/wow: {}", wow);
                    return Optional.of(wow.toUri());
                }
            }
        }
        return Optional.empty();
    }

    public Optional<URI> http2file(String f) {
        logger.trace("http2fileS: {}", f);
        if (f.startsWith(hostname)) {
            String cut = f.substring(hostname.length());
            logger.trace("http2fileS/cut: {}", cut);
            for (PathMap pm : sortByHttp) {
                String key = pm.http();
                logger.trace("http2fileS/key: {}", key);
                if (cut.startsWith(key)) {
                    String chunk = cut.substring(key.length());
                    logger.trace("http2fileS/chunk: {}", chunk);
                    if (chunk.isEmpty()) {
                        return Optional.of(Path.of(pm.file()).toUri());
                    } else {
                        return Optional.of(Path.of(pm.file(), chunk).toUri());
                    }
                }
            }
        }
        return Optional.empty();
    }
    
    public Optional<URI> file2http(String furi) {
        logger.debug("file2httpS {}", furi);
        for (PathMap pathmap : sortByFile) {
            String key = pathmap.file();
            logger.debug("file2httpS/key {}",key);
            if (furi.startsWith(key)) {
                String chunk = furi.substring(key.length());
                logger.debug("file2httpS/chunk {}",chunk);
                URI uri;
                try {
                    uri = new URI(hostname+pathmap.http()+chunk);
                    logger.debug("file2httpS/uri {}", uri);
                    return Optional.of(uri);
                } catch (URISyntaxException ex) {
                    logger.error("Problem with converting file uri to http uri {}", furi);
                }
            }
        }
        return Optional.empty();
    }
        
    public Optional<URI> file2http(URI furi) {
        if (OperatingSystemInfo.ifWindows()) {
            return file2http(furi.getPath().substring(1));
        }
        return file2http(furi.getPath());
    }
    
    public static PathMapper getPathMapper() {
        HalcyonSettings settings = HalcyonSettings.getSettings();
        if (pathmapper == null) {
            pathmapper = new PathMapper(settings);            
        }
        return pathmapper;
    }
    
    public static PathMapper getPathMapper(HalcyonSettings settings) {        
        if (pathmapper == null) {
            pathmapper = new PathMapper(settings);            
        }
        
        pathmapper.sortByFile.forEach(p->System.out.println("by file ---> "+p));
        pathmapper.sortByHttp.forEach(p->System.out.println("by http ---> "+p));
        
        return pathmapper;
    }    
}
