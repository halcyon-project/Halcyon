package com.ebremer.halcyon.server.utils;

import com.ebremer.halcyon.lib.OperatingSystemInfo;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
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
        return (uri == null) ? Optional.empty() : http2file(uri.toString());
    }

    public Optional<URI> http2file(String f) {
        logger.trace("http2fileS: {}", f);
        if (f == null || !f.startsWith(hostname)) {
            return Optional.empty();
        }
        String cut = f.substring(hostname.length());
        logger.trace("http2fileS/cut: {}", cut);
        for (PathMap pm : sortByHttp) {
            String key = pm.http();
            logger.trace("http2fileS/key: {}", key);
            if (cut.startsWith(key)) {
                String chunk = cut.substring(key.length());
                logger.trace("http2fileS/chunk: {}", chunk);
                return resolveWithin(pm.file(), chunk);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve {@code chunk} under the resource root {@code base} and PROVE the
     * result stays inside it (H9).
     * <p>
     * This used to be a bare {@code Path.of(base, chunk).toUri()} with no
     * normalization and no containment check. {@code chunk} is attacker-supplied:
     * for the image path it comes out of the {@code ?iiif=} QUERY PARAMETER, not
     * the request URI, so the servlet container never normalizes it — a
     * {@code ../} walked straight out of the configured resource root.
     * {@code startsWith} compares path COMPONENTS, so a sibling root whose name
     * merely shares a prefix cannot masquerade as being inside this one.
     *
     * @return the contained target, or empty if it escapes / is unusable — which
     *         callers already treat as "not found" (fail closed).
     */
    private static Optional<URI> resolveWithin(String base, String chunk) {
        try {
            Path root = Path.of(base).toAbsolutePath().normalize();
            Path target = (chunk == null || chunk.isEmpty())
                    ? root
                    : root.resolve(chunk).normalize();
            if (!target.startsWith(root)) {
                logger.warn("Refusing path that escapes its resource root: base={} chunk={}", base, chunk);
                return Optional.empty();
            }
            logger.trace("http2fileS/target: {}", target);
            return Optional.of(target.toUri());
        } catch (InvalidPathException ex) {
            logger.warn("Refusing unusable path: base={} chunk={} ({})", base, chunk, ex.getMessage());
            return Optional.empty();
        }
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
