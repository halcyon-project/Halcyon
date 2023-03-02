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
        //System.out.println("Building FL Pool X...");
    }
    
    @Override
    public FL borrowObject(final URI key) throws Exception {
        //System.out.println("borrowObject : "+key.toString());
        return (FL) super.borrowObject(key);
    }
    
    @Override
    public void returnObject(final URI key, final FL reader) {
        //System.out.println("returnObject : "+key);
        super.returnObject(key, reader);
    }
}
