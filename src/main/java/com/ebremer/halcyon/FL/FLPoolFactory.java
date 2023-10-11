package com.ebremer.halcyon.FL;

import java.net.URI;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.DestroyMode;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 *
 * @author erich
 */
public class FLPoolFactory extends BaseKeyedPooledObjectFactory<URI, FL> {
    
    public FLPoolFactory(String base) {}

    @Override
    public FL create(URI uri) throws Exception {
        return new FL(uri);
    }
    
    @Override
    public PooledObject<FL> wrap(FL value) {
        return new DefaultPooledObject<>(value);
    }

    @Override
    public void destroyObject(URI key, PooledObject p, DestroyMode mode) throws Exception {
        System.out.println("Closing FL "+key);
        FL fl = (FL) p.getObject();
        fl.close();
        super.destroyObject(key, p, mode);
    }  
}
