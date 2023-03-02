package com.ebremer.halcyon.imagebox;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.DestroyMode;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 *
 * @author erich
 */
public class ImageReaderPoolFactory extends BaseKeyedPooledObjectFactory<String, NeoTiler> {
    
    @Override
    public NeoTiler create(String key) throws Exception {
        return new NeoTiler(key);
    }

    @Override
    public PooledObject<NeoTiler> wrap(NeoTiler value) {
        return new DefaultPooledObject<>(value);
    }

    @Override
    public void destroyObject(String key, PooledObject p, DestroyMode mode) throws Exception {
        //System.out.println("Destroying Image Reader ");
        super.destroyObject(key, p, mode);
    }   
}