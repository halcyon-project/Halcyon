package com.ebremer.halcyon.beakstuff;

import com.ebremer.beakgraph.control.VectorCache;
import com.ebremer.beakgraph.ng.BeakGraph;
import com.ebremer.halcyon.server.utils.PathMapper;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
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
    
    public BeakGraphPoolFactory() {}

    @Override
    public BeakGraph create(URI uri) throws Exception {
        logger.trace("Creating BeakGraph "+uri);
        instanceCountMap.computeIfAbsent(uri, k -> new AtomicInteger()).incrementAndGet();
        if (uri.getScheme().startsWith("http")) {
            Optional<URI> src = PathMapper.getPathMapper().http2file(uri);
            if (src.isPresent()) {
                return new BeakGraph(src.get(), uri);
            }
            throw new Error("Cannot locate local file!!!");
        }
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
    public void destroyObject(URI uri, PooledObject p, DestroyMode mode) throws Exception {
        logger.trace("Closing BeakGraph "+uri);
        instanceCountMap.get(uri).decrementAndGet();
        ((BeakGraph) p.getObject()).close();
        super.destroyObject(uri, p, mode);
        if (getInstanceCount(uri)==0) {
            logger.debug("Flushing VectorCache for "+uri+" # of BG: "+ getInstanceCount(uri));
            VectorCache.getInstance().removeFromCache(uri);
        }                
    }  
}
