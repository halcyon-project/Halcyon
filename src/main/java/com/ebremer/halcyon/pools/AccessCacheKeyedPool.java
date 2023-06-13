package com.ebremer.halcyon.pools;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;

/**
 *
 * @author erich
 * @param <String>
 * @param <AccessCache>
 */
public class AccessCacheKeyedPool<String, AccessCache> extends GenericKeyedObjectPool<String, AccessCache> {
    private final Set<String> keys;
    
    public AccessCacheKeyedPool(AccessCachePoolFactory factory, AccessCacheKeyedPoolConfig config) {
        super((BaseKeyedPooledObjectFactory)factory, config);
        keys = Collections.synchronizedSet(new HashSet<>());
    }
    
    public Set<String> getKeys() {
        return keys;
    }
    
    public void invalidateAndEmptyPoolForKey(String key) {
        this.clear(key);       
    }
    
    public void invalidateAndEmptyPoolForAllKeys() {
        this.clear();
    }
    
    @Override
    public AccessCache borrowObject(final String key) throws Exception {
        //System.out.println(this.getBorrowedCount()+" == "+this.getNumActive()+" borrowObject --> "+key);
        keys.add(key);
        return (AccessCache) super.borrowObject(key);
    }
    
    @Override
    public void returnObject(final String key, final AccessCache reader) {
        //System.out.println(this.getNumActive(key)+" == "+this.getNumActive()+" returnObject --> "+key);
        super.returnObject(key, reader);
    }
}
