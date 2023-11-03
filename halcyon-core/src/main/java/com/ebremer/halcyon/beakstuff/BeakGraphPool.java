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
            BeakGraphKeyedPoolConfig config = new BeakGraphKeyedPoolConfig<>();
            //config.setMaxTotalPerKey(Runtime.getRuntime().availableProcessors());
            config.setMaxTotalPerKey(5);
            config.setMinIdlePerKey(0);
            config.setMaxWait(Duration.ofMillis(20000));
            config.setBlockWhenExhausted(true);
            config.setMinEvictableIdleTime(Duration.ofMillis(21000));
            config.setTimeBetweenEvictionRuns(Duration.ofMillis(21000));
            pool = new BeakGraphKeyedPool<>(new BeakGraphPoolFactory(config.getBase()),config);
        }
        return pool;
    }
}
