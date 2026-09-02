package com.ebremer.halcyon.pools;

import java.time.Duration;

/**
 *
 * @author erich
 */
public class AccessCachePool {
    private static AccessCacheKeyedPool pool;
    
    private AccessCachePool() {}
    
    public static synchronized AccessCacheKeyedPool getPool() {
        if (pool == null) {
            return getPool(new AccessCacheKeyedPoolConfig<AccessCache>());
        }
        return pool;
    }
    
    public static synchronized AccessCacheKeyedPool getPool(AccessCacheKeyedPoolConfig<AccessCache> config) {
        if (pool == null) {
            config.setMaxTotalPerKey(5);
            config.setMinIdlePerKey(0);
            config.setMaxWait(Duration.ofMillis(1000));
            config.setBlockWhenExhausted(true);
            config.setMinEvictableIdleTime(Duration.ofMillis(600000));
            config.setTimeBetweenEvictionRuns(Duration.ofMillis(600000));
            pool = new AccessCacheKeyedPool(new AccessCachePoolFactory(), config);
        }
        return pool;
    }
}
