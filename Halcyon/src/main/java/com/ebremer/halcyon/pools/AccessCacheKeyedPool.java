package com.ebremer.halcyon.pools;

import com.ebremer.halcyon.gui.HalcyonSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
    
    public Set<String> getKeys2() {
        return keys;
    }
    
    @Override
    public List<String> getKeys() {
        HalcyonSession ha;
        System.out.println("SOMETHING IS CALLING THIS!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        return new ArrayList<>();
    }
    
    public void invalidateAndEmptyPoolForKey(String key) {
        this.clear(key);       
    }
    
    public void invalidateAndEmptyPoolForAllKeys() {
        this.clear();
    }
    
    @Override
    public AccessCache borrowObject(final String key) throws Exception {
        keys.add(key);
        return (AccessCache) super.borrowObject(key);
    }
    
    @Override
    public void returnObject(final String key, final AccessCache reader) {
        super.returnObject(key, reader);
    }
}
