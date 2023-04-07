package com.ebremer.halcyon.pools;

import java.time.Duration;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 *
 * @author erich
 * @param <T>
 */
public class AccessCacheKeyedPoolConfig<T> extends GenericKeyedObjectPoolConfig<T> {
    
    public AccessCacheKeyedPoolConfig() {
        super();
            this.setMaxTotalPerKey(10);
            this.setMinIdlePerKey(0);
            this.setMaxWait(Duration.ofMillis(50000));
            this.setBlockWhenExhausted(true);
            this.setMinEvictableIdleTime(Duration.ofMillis(24*60*60000));
            this.setTimeBetweenEvictionRuns(Duration.ofMillis(60000));
        }
}
