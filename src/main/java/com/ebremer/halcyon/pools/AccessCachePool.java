package com.ebremer.halcyon.pools;

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
            //config.setMaxTotalPerKey(10);
            //config.setMinIdlePerKey(0);
            //config.setMaxWait(Duration.ofMillis(5000));
            //config.setBlockWhenExhausted(true);
            //config.setMinEvictableIdleTime(Duration.ofMillis(60000));
            //config.setTimeBetweenEvictionRuns(Duration.ofMillis(60000));
            pool = new AccessCacheKeyedPool<>(new AccessCachePoolFactory(),config);
        }
        return pool;
    }
}
