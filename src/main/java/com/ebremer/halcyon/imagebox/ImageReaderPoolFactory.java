package com.ebremer.halcyon.imagebox;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.DestroyMode;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 *
 * @author erich
 */
public class ImageReaderPoolFactory extends BaseKeyedPooledObjectFactory<String, ImageTiler> {
    
    @Override
    public ImageTiler create(String key) throws Exception {
        return new ImageTiler(key);
    }

    @Override
    public PooledObject<ImageTiler> wrap(ImageTiler value) {
        return new DefaultPooledObject<>(value);
    }

    @Override
    public void destroyObject(String key, PooledObject p, DestroyMode mode) throws Exception {
        System.out.println("Destroying Image Reader ---> "+key);
        ImageTiler nt = (ImageTiler) p.getObject();
        nt.close();
        super.destroyObject(key, p, mode);
    }   
}