package com.ebremer.halcyon.imagebox.TE;

import java.net.URI;
import java.time.Duration;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 *
 * @author erich
 */
public class TileRequestEnginePool extends GenericKeyedObjectPool<URI, TileRequestEngine> {
    private static TileRequestEnginePool pool;

    private TileRequestEnginePool(BaseKeyedPooledObjectFactory<URI, TileRequestEngine> factory, GenericKeyedObjectPoolConfig<TileRequestEngine> config) {
        super(factory, config);
    }
        
    @Override
    public TileRequestEngine borrowObject(final URI key) throws Exception {
        return super.borrowObject(key);
    }
    
    public static synchronized TileRequestEnginePool getPool() {
        GenericKeyedObjectPoolConfig<TileRequestEngine> config = new GenericKeyedObjectPoolConfig<>();
        config.setMaxTotalPerKey(8);
        config.setMinIdlePerKey(0);
        config.setMaxWait(Duration.ofMillis(60000));
        config.setBlockWhenExhausted(true);
        config.setMinEvictableIdleTime(Duration.ofMillis(60000));
        config.setTimeBetweenEvictionRuns(Duration.ofMillis(60000));
        if (pool==null) {
            pool = new TileRequestEnginePool(new TileRequestEnginePoolFactory(), config);
        }
        return pool;
    }
}
