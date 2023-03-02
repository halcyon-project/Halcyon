package com.ebremer.halcyon.beakstuff;

import com.ebremer.beakgraph.rdf.BeakGraph;
import java.net.URI;
import java.time.Duration;

/**
 *
 * @author erich
 */
public class BeakGraphPool {
    private static BeakGraphKeyedPool<URI,BeakGraph> pool;
    
    private BeakGraphPool() {}
    
    public static synchronized BeakGraphKeyedPool<URI, BeakGraph> getPool() {
        if (pool == null) {
            return getPool(new BeakGraphKeyedPoolConfig<>());
        }
        return pool;
    }
    
    public static synchronized BeakGraphKeyedPool<URI, BeakGraph> getPool(BeakGraphKeyedPoolConfig config) {
        if (pool == null) {
            config.setMaxTotalPerKey(10);
            config.setMinIdlePerKey(5);
            config.setMaxWait(Duration.ofMillis(10000));
            config.setBlockWhenExhausted(true);
            pool = new BeakGraphKeyedPool<>(new BeakGraphPoolFactory(config.getBase()),config);
        }
        return pool;
    }
}
