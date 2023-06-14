package com.ebremer.halcyon.imagebox;

import java.time.Duration;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 *
 * @author erich
 */
public class ImageReaderPool {
    private static ImageReaderKeyedPool<String,ImageTiler> pool;
    
    public ImageReaderPool() {}
    
    public static synchronized ImageReaderKeyedPool<String, ImageTiler> getPool() {
        if (pool == null) {
            GenericKeyedObjectPoolConfig<ImageTiler> config = new GenericKeyedObjectPoolConfig<>();
            config.setMaxTotalPerKey(5);
            config.setMinIdlePerKey(0);
            config.setMaxWait(Duration.ofMillis(60000));
            config.setBlockWhenExhausted(true);
            config.setMinEvictableIdleTime(Duration.ofMillis(60000));
            config.setTimeBetweenEvictionRuns(Duration.ofMillis(60000));
            pool = new ImageReaderKeyedPool<>(new ImageReaderPoolFactory(),config);
        }
        return pool;
    }   
}
