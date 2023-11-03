package com.ebremer.halcyon.FL;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;

/**
 *
 * @author erich
 * @param <URI>
 * @param <FL>
 */
public class FLKeyedPool<URI, FL> extends GenericKeyedObjectPool<URI, FL> {
    
    public FLKeyedPool(FLPoolFactory factory, FLKeyedPoolConfig config) {
        super((BaseKeyedPooledObjectFactory)factory, config);
    }
    
    @Override
    public FL borrowObject(final URI key) throws Exception {
        //System.out.println("FL Borrow --> "+key);
        return (FL) super.borrowObject(key);
    }
    
    @Override
    public void returnObject(final URI key, final FL reader) {
        //System.out.println("FL Return --> "+key);
        super.returnObject(key, reader);
    }
}
