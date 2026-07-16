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
