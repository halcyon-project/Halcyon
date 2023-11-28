package com.ebremer.halcyon.FL;

import com.ebremer.beakgraph.ng.BGDatasetGraph;
import com.ebremer.beakgraph.ng.BeakGraph;
import com.ebremer.halcyon.beakstuff.BeakGraphPool;
import com.ebremer.ns.HAL;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.DestroyMode;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class FLPoolFactory extends BaseKeyedPooledObjectFactory<URI, FL> {
    private final Map<URI, AtomicInteger> instanceCountMap = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(FLPoolFactory.class);
    
    public FLPoolFactory(String base) {}

    @Override
    public FL create(URI uri) throws Exception {
        logger.trace("Creating FL "+uri+" # of Objects Alive : "+getInstanceCount(uri));
        instanceCountMap.computeIfAbsent(uri, k -> new AtomicInteger()).incrementAndGet();
        BeakGraph bg = BeakGraphPool.getPool().borrowObject(uri);
        Dataset ds = DatasetFactory.wrap(new BGDatasetGraph(bg));
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            ask
            where { ?grid a hal:Segmentation }
            """
        );
        pss.setNsPrefix("hal", HAL.NS);
        boolean type = QueryExecutionFactory.create(pss.toString(), ds.getDefaultModel()).execAsk();
        BeakGraphPool.getPool().returnObject(uri, bg);
        if (type) {
            logger.trace("Segmentation type");
            return new FLSegmentation(uri);
        } else {
            logger.trace("Heatmap type");
            return new FLHeatmap(uri);
        }        
    }
    
    @Override
    public PooledObject<FL> wrap(FL value) {
        return new DefaultPooledObject<>(value);
    }
    
    public int getInstanceCount(URI key) {
        AtomicInteger count = instanceCountMap.get(key);
        return count == null ? 0 : count.get();
    }

    @Override
    public void destroyObject(URI key, PooledObject p, DestroyMode mode) throws Exception {
        logger.trace("Closing FL "+key+" # of Objects Alive : "+getInstanceCount(key));
        instanceCountMap.get(key).decrementAndGet();
        FL fl = (FL) p.getObject();
        fl.close();
        super.destroyObject(key, p, mode);
    }  
}
