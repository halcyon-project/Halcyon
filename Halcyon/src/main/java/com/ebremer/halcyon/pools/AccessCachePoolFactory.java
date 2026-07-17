package com.ebremer.halcyon.pools;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.DestroyMode;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class AccessCachePoolFactory extends BaseKeyedPooledObjectFactory<String, AccessCache> {
    private static final Logger logger = LoggerFactory.getLogger(AccessCachePoolFactory.class);
    
    public AccessCachePoolFactory() {}

    @Override
    public AccessCache create(String key) throws Exception {
        //System.out.println("create AccessCache "+key);
        return new AccessCache();
    }
    
    @Override
    public PooledObject<AccessCache> wrap(AccessCache value) {
        return new DefaultPooledObject<>(value);
    }

    @Override
    public void destroyObject(String key, PooledObject p, DestroyMode mode) throws Exception {
        logger.debug("Destroying AccessCache for {}", key);
        super.destroyObject(key, p, mode);
    }  
}
