package com.ebremer.halcyon.server.utils;

import com.ebremer.halcyon.filereaders.ImageReader;
import com.ebremer.halcyon.filereaders.FileReaderFactoryProvider;
import com.ebremer.halcyon.lib.FileUtils;
import java.io.File;
import java.net.URI;
import java.util.Optional;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.DestroyMode;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 * @param <K>
 * @param <V>
 */
public class ImageReaderPoolFactory<K, V> extends BaseKeyedPooledObjectFactory<URI, ImageReader> {

    private static final Logger logger = LoggerFactory.getLogger(ImageReaderPoolFactory.class);

    /**
     * Server-registered image sources, keyed by a SYNTHETIC identifier.
     *
     * <p>This is the trusted sibling of the PathMapper rule below, and it must
     * not weaken it (H9): {@code ?iiif=} identifiers remain confined to the
     * configured resource roots, and {@code file:} URIs remain refused. What
     * this adds is a channel only server-side CODE can use — the LWS storage
     * bridge resolves a resource's blob path (after its own ACP check) and
     * registers it under an unguessable {@code urn:} key it mints itself. A
     * request can only reach such an entry by presenting the exact key, which
     * never leaves the server, so nothing here is reachable from a query
     * parameter. Entries are tiny (URI → path) and live for the process;
     * a re-registration under a fresh key simply strands the old entry.
     */
    private static final java.util.concurrent.ConcurrentHashMap<URI, java.nio.file.Path> TRUSTED =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Register a server-resolved image source under a synthetic (non-http) key. */
    public static void registerTrustedSource(URI key, java.nio.file.Path file) {
        String scheme = key.getScheme();
        if (scheme == null || scheme.equals("http") || scheme.equals("https")
                || scheme.equals("file")) {
            // An http(s) key would shadow PathMapper's rules; a file key would
            // recreate the H9 primitive. Synthetic schemes only.
            throw new IllegalArgumentException("trusted source keys must use a synthetic scheme: " + key);
        }
        TRUSTED.put(key, file);
    }

    public ImageReaderPoolFactory() {}
    
    /**
     * H9: the ONLY identifier accepted here is one PathMapper resolves to a file
     * INSIDE a configured resource root.
     * <p>
     * This is reached from the {@code ?iiif=} query parameter, which is entirely
     * attacker-controlled, and it used to fall through to two arbitrary-read
     * primitives when PathMapper declined the URI:
     * <ul>
     *   <li>{@code uri.getScheme().equals("file")} → {@code uri.getPath().substring(1)},
     *       i.e. {@code ?iiif=file:/D:/anything.tif/full/512,/0/default.jpg} read
     *       ANY local file with a known image extension, PathMapper bypassed
     *       entirely;</li>
     *   <li>a final {@code else getthis = uri.toString()}, which handed any other
     *       scheme straight to {@code new File(...)} — so a bare
     *       {@code D:/secret/x.tif} (parsed as scheme "D") worked too.</li>
     * </ul>
     * Both are gone: unresolvable identifiers are refused. (The old chain also
     * NPE'd on a scheme-less URI, since it called {@code getScheme().equals(...)}
     * before any null check.)
     */
    @Override
    public ImageReader create(URI uri) throws Exception {
        logger.trace("creating {}", uri);
        java.nio.file.Path trusted = TRUSTED.get(uri);
        if (trusted != null) {
            String text = trusted.getFileName().toString();
            String tExt = FileUtils.getExtension(text);
            if (FileReaderFactoryProvider.contains(tExt)) {
                return (ImageReader) FileReaderFactoryProvider.getReaderForFormat(tExt)
                        .create(trusted.toUri(), uri);
            }
            throw new Error("Don't know how to handle extensions with : " + tExt);
        }
        Optional<URI> x = PathMapper.getPathMapper().http2file(uri);
        if (x.isEmpty()) {
            logger.warn("Refusing image identifier outside the configured resource roots: {}", uri);
            throw new SecurityException("Image identifier is not within a configured resource root");
        }
        String getthis = x.get().getPath().replace("%20", " ");
        URI xuri = (new File(getthis)).toURI();
        logger.trace("translated "+xuri);
        switch (xuri.getScheme()) {
            case "file" -> {
                String ext = FileUtils.getExtension(getthis.replace("/", ""));
                if (FileReaderFactoryProvider.contains(ext)) {
                    return (ImageReader) FileReaderFactoryProvider.getReaderForFormat(ext).create(xuri, uri);
                }
                throw new Error("Don't know how to handle extensions with : "+ext);
            }
            default -> throw new Error("don't know how to handle --> "+uri.getScheme());
        }
    }

    @Override
    public PooledObject<ImageReader> wrap(ImageReader value) {
        return new DefaultPooledObject<>(value);
    }
    
   @Override
    public void destroyObject(URI key, PooledObject p, DestroyMode mode) throws Exception {
        logger.debug("destroyObject {}", key);
        ImageReader nt = (ImageReader) p.getObject();
        nt.close();
        super.destroyObject(key, p, mode);
    }  
}
