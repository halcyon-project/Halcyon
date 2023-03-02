package com.ebremer.halcyon.beakstuff;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;

/**
 *
 * @author erich
 * @param <URI>
 * @param <BeakGraph>
 */
public class BeakGraphKeyedPool<URI, BeakGraph> extends GenericKeyedObjectPool<URI, BeakGraph> {
    
    public BeakGraphKeyedPool(BeakGraphPoolFactory factory, BeakGraphKeyedPoolConfig config) {
        super((BaseKeyedPooledObjectFactory)factory, config);
    }
    
    @Override
    public BeakGraph borrowObject(final URI key) throws Exception {
        System.out.println("borrowObject : "+key.toString());
        BeakGraph f = (BeakGraph) super.borrowObject(key);
        System.out.println(f==null);
        return f;
    }
    
    @Override
    public void returnObject(final URI key, final BeakGraph reader) {
        super.returnObject(key, reader);
    }
}
