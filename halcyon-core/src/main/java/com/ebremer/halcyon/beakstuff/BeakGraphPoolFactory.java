package com.ebremer.halcyon.beakstuff;

import com.ebremer.beakgraph.control.VectorCache;
import com.ebremer.beakgraph.ng.BeakGraph;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
public class BeakGraphPoolFactory extends BaseKeyedPooledObjectFactory<URI, BeakGraph> {
    private final Map<URI, AtomicInteger> instanceCountMap = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(BeakGraphPoolFactory.class);
    
    public BeakGraphPoolFactory(String base) {}

    @Override
    public BeakGraph create(URI uri) throws Exception {
        logger.trace("Creating BeakGraph "+uri);
        instanceCountMap.computeIfAbsent(uri, k -> new AtomicInteger()).incrementAndGet();
        return new BeakGraph(uri);
    }
    
    @Override
    public PooledObject<BeakGraph> wrap(BeakGraph value) {
        return new DefaultPooledObject<>(value);
    }
    
    public int getInstanceCount(URI key) {
        AtomicInteger count = instanceCountMap.get(key);
        return count == null ? 0 : count.get();
    }

    @Override
    public void destroyObject(URI key, PooledObject p, DestroyMode mode) throws Exception {
        logger.trace("Closing BeakGraph "+key);
        instanceCountMap.get(key).decrementAndGet();
        ((BeakGraph) p.getObject()).close();
        super.destroyObject(key, p, mode);
        if (getInstanceCount(key)==0) {
            logger.debug("Flushing VectorCache for "+key+" # of BG: "+ getInstanceCount(key));
            VectorCache.getInstance().removeFromCache(key);
        }                
    }  
}
