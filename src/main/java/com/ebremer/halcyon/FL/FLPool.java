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
            return getPool(new FLKeyedPoolConfig<>());
        }
        return pool;
    }
    
    public static synchronized FLKeyedPool<URI, FL> getPool(FLKeyedPoolConfig config) {
        if (pool == null) {
            config.setMaxTotalPerKey(Runtime.getRuntime().availableProcessors());
            config.setMinIdlePerKey(0);
            config.setMaxWait(Duration.ofMillis(60000));
            config.setBlockWhenExhausted(true);
            config.setMinEvictableIdleTime(Duration.ofMillis(60000));
            config.setTimeBetweenEvictionRuns(Duration.ofMillis(60000));
            pool = new FLKeyedPool<>(new FLPoolFactory(config.getBase()),config);
        }
        return pool;
    }
}
