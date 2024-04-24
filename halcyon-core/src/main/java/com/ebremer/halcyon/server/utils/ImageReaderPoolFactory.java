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

/**
 *
 * @author erich
 * @param <K>
 * @param <V>
 */
public class ImageReaderPoolFactory<K, V> extends BaseKeyedPooledObjectFactory<URI, ImageReader> {
    
    public ImageReaderPoolFactory() {}
    
    @Override
    public ImageReader create(URI uri) throws Exception {
        String getthis;
        Optional<URI> x = PathMapper.getPathMapper().http2file(uri);
        if (x.isPresent()) {
            getthis = x.get().getPath().replace("%20", " ");
        } else if ((uri.getScheme().equals("https")||uri.getScheme().equals("http"))) {
            throw new Error("remote http access being added back in.  Not available at the moment : "+uri.toString());
        } else {
            getthis = uri.toString();
        }
        URI xuri = (new File(getthis)).toURI();
        switch (xuri.getScheme()) {
            case "file":
                String ext = FileUtils.getExtension(getthis);
                if (FileReaderFactoryProvider.contains(ext)) {
                    return (ImageReader) FileReaderFactoryProvider.getReaderForFormat(ext).create(xuri, uri);
                }
                throw new Error("Don't know how to handle extensions with : "+ext);
            default: throw new Error("don't know how to handle --> "+uri.getScheme());
        }
    }

    @Override
    public PooledObject<ImageReader> wrap(ImageReader value) {
        return new DefaultPooledObject<>(value);
    }
    
   @Override
    public void destroyObject(URI key, PooledObject p, DestroyMode mode) throws Exception {
        ImageReader nt = (ImageReader) p.getObject();
        nt.close();
        super.destroyObject(key, p, mode);
    }  
}

