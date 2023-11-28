package com.ebremer.halcyon.server.utils;

import com.ebremer.halcyon.filereaders.ImageReader;
import com.ebremer.halcyon.filereaders.FileReaderFactoryProvider;
import com.ebremer.halcyon.lib.FileUtils;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.DestroyMode;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 *
 * @author erich
 */
public class ImageReaderPoolFactory extends BaseKeyedPooledObjectFactory<URI, ImageReader> {
    
    public ImageReaderPoolFactory() {}
    
    @Override
    public ImageReader create(URI uri) throws Exception {
        String f = uri.toString();
        String getthis;
        HalcyonSettings settings = HalcyonSettings.getSettings();
        String mainpath = settings.getHostName();
        Path x = null;
        if (f.startsWith(mainpath)) {
            String cut = f.substring(mainpath.length());
            HashMap<String, String> mappings = settings.getmappings();
            for (String key : mappings.keySet()) {
                if (cut.startsWith(key)) {
                    String chunk = cut.substring(key.length());
                    x = Path.of(mappings.get(key), chunk);
                }
            }
        }
        if (x !=null) {
            getthis = x.toString().replace("%20", " ");
        } else if ((f.startsWith("https://")||f.startsWith("http://"))) {
            throw new Error("remote http access being added back in.  Not available at the moment");
        } else {
            getthis = f;
        }
        URI xuri = (new File(getthis)).toURI();
        switch (xuri.getScheme()) {
            case "file":
                String ext = FileUtils.getExtension(getthis);
                if (FileReaderFactoryProvider.contains(ext)) {
                    return (ImageReader) FileReaderFactoryProvider.getReaderForFormat(ext).create(xuri);
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
        //System.out.println("Destroying Image Reader ---> "+key);
        ImageReader nt = (ImageReader) p.getObject();
        nt.close();
        super.destroyObject(key, p, mode);
    }  
}

