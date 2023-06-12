package com.ebremer.halcyon.pools;

import java.time.Duration;

/**
 *
 * @author erich
 */
public class AccessCachePool {
    private static AccessCacheKeyedPool<String,AccessCache> pool;
    
    private AccessCachePool() {}
    
    public static synchronized AccessCacheKeyedPool<String, AccessCache> getPool() {
        if (pool == null) {
            return getPool(new AccessCacheKeyedPoolConfig<>());
        }
        return pool;
    }
    
    public static synchronized AccessCacheKeyedPool<String, AccessCache> getPool(AccessCacheKeyedPoolConfig config) {
        if (pool == null) {
            config.setMaxTotalPerKey(1);
            config.setMinIdlePerKey(0);
            config.setMaxWait(Duration.ofMillis(60000));
            config.setBlockWhenExhausted(true);
            config.setMinEvictableIdleTime(Duration.ofMillis(600000));
            config.setTimeBetweenEvictionRuns(Duration.ofMillis(600000));
            pool = new AccessCacheKeyedPool<>(new AccessCachePoolFactory(),config);
        }
        return pool;
    }
}
