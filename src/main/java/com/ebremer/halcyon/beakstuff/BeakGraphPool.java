package com.ebremer.halcyon.beakstuff;

import com.ebremer.beakgraph.ng.BeakGraph;
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
            config.setMaxTotalPerKey(5);
            config.setMinIdlePerKey(0);
            config.setMaxWait(Duration.ofMillis(60000));
            config.setBlockWhenExhausted(true);
            config.setMinEvictableIdleTime(Duration.ofMillis(61000));
            config.setTimeBetweenEvictionRuns(Duration.ofMillis(61000));
            pool = new BeakGraphKeyedPool<>(new BeakGraphPoolFactory(config.getBase()),config);
        }
        return pool;
    }
}
