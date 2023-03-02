package com.ebremer.halcyon.imagebox;

import java.time.Duration;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;


/**
 *
 * @author erich
 */
public class ImageReaderPool {
    private static ImageReaderKeyedPool<String,NeoTiler> pool;
    
    public ImageReaderPool() {}
    
    public static synchronized ImageReaderKeyedPool<String, NeoTiler> getPool() {
        if (pool == null) {
            GenericKeyedObjectPoolConfig<NeoTiler> config = new GenericKeyedObjectPoolConfig<>();
            config.setMaxTotalPerKey(6);
            config.setMinIdlePerKey(0);
            config.setMaxWait(Duration.ofMillis(10000));
            config.setBlockWhenExhausted(true);
            config.setTimeBetweenEvictionRuns(Duration.ofMillis(10000));
            pool = new ImageReaderKeyedPool<>(new ImageReaderPoolFactory(),config);
        }
        return pool;
    }   
}