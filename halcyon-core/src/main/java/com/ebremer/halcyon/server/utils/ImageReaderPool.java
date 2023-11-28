package com.ebremer.halcyon.server.utils;

import com.ebremer.halcyon.filereaders.ImageReader;
import java.net.URI;
import java.time.Duration;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 *
 * @author erich
 */
public class ImageReaderPool extends GenericKeyedObjectPool<URI, ImageReader> {
    private static ImageReaderPool pool;

    private ImageReaderPool(BaseKeyedPooledObjectFactory<URI, ImageReader> factory, GenericKeyedObjectPoolConfig<ImageReader> config) {
        super(factory, config);
    }
        
    @Override
    public ImageReader borrowObject(final URI key) throws Exception {
        return super.borrowObject(key);
    }
    
    @Override
    public void returnObject(final URI key, final ImageReader obj) {
        super.returnObject(key, obj);
    }
    
    public static synchronized ImageReaderPool getPool() {
        GenericKeyedObjectPoolConfig<ImageReader> config = new GenericKeyedObjectPoolConfig<>();
        config.setMaxTotalPerKey(Runtime.getRuntime().availableProcessors());
        config.setMinIdlePerKey(0);
        //config.setMaxWait(Duration.ofMillis(60000));
        config.setBlockWhenExhausted(true);
        config.setMinEvictableIdleTime(Duration.ofMillis(60000));
        config.setTimeBetweenEvictionRuns(Duration.ofMillis(60000));
        if (pool==null) {
            pool = new ImageReaderPool(new ImageReaderPoolFactory(), config);
        }
        return pool;
    }
}
