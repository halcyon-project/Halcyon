package com.ebremer.halcyon.FL;

import java.net.URI;
import java.time.Duration;

/**
 *
 * @author erich
 */
public class FLPool {
    private static FLKeyedPool<URI,FL> pool;
    
    private FLPool() {}
    
    public static synchronized FLKeyedPool<URI, FL> getPool() {
        if (pool == null) {
            FLKeyedPoolConfig config = new FLKeyedPoolConfig<>();
            config.setMaxTotalPerKey(5);
            config.setMinIdlePerKey(0);
            config.setMaxWait(Duration.ofMillis(20000));
            config.setBlockWhenExhausted(true);
            config.setMinEvictableIdleTime(Duration.ofMillis(20000));
            config.setTimeBetweenEvictionRuns(Duration.ofMillis(20000));
            pool = new FLKeyedPool<>(new FLPoolFactory(config.getBase()),config);
        }
        return pool;
    }
}
