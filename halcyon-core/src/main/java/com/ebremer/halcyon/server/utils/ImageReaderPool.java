package com.ebremer.halcyon.server.utils;

import com.ebremer.halcyon.filereaders.ImageReader;
import java.net.URI;
import java.time.Duration;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thread-safe, keyed object pool for managing ImageReader instances.
 * Uses Apache Commons Pool2 to recycle readers based on their URI.
 * * @author erich
 * Refined for Halcyon Server
 */
public class ImageReaderPool extends GenericKeyedObjectPool<URI, ImageReader> {
    
    private static final Logger logger = LoggerFactory.getLogger(ImageReaderPool.class);

    /**
     * Private constructor to enforce Singleton pattern via the inner holder.
     */
    private ImageReaderPool(ImageReaderPoolFactory<URI, ImageReader> factory, GenericKeyedObjectPoolConfig<ImageReader> config) {
        super(factory, config);
    }

    /**
     * Lazy Initialization Holder Class. 
     * This ensures the pool is only created when first accessed, in a thread-safe manner 
     * without requiring synchronized blocks on every getPool() call.
     */
    private static class Holder {
        private static final ImageReaderPool INSTANCE;

        static {
            GenericKeyedObjectPoolConfig<ImageReader> config = new GenericKeyedObjectPoolConfig<>();
            
            // Allocate one reader per CPU core per unique URI to maximize throughput
            config.setMaxTotalPerKey(Runtime.getRuntime().availableProcessors());
            config.setMinIdlePerKey(0);
            
            // Block requesting threads if the pool for a specific URI is exhausted
            config.setBlockWhenExhausted(true);
            config.setMaxWait(Duration.ofMillis(30000)); // 30 second timeout
            
            // Eviction settings: remove idle objects after 1 minute of inactivity
            config.setMinEvictableIdleDuration(Duration.ofMinutes(1));
            config.setTimeBetweenEvictionRuns(Duration.ofMinutes(1));
            
            // Ensure objects are still valid when borrowed (optional, but safer)
            config.setTestOnBorrow(false); 

            INSTANCE = new ImageReaderPool(new ImageReaderPoolFactory<>(), config);
        }
    }

    /**
     * Accessor for the Singleton Pool instance.
     * @return the global ImageReaderPool
     */
    public static ImageReaderPool getPool() {
        return Holder.INSTANCE;
    }
            
    @Override
    public ImageReader borrowObject(final URI key) throws Exception {
        if (logger.isTraceEnabled()) {
            logger.trace("Borrowing reader for: {}\n{}", key, getStatus());
        }
        return super.borrowObject(key);
    }
    
    @Override
    public void returnObject(final URI key, final ImageReader obj) {
        if (logger.isTraceEnabled()) {
            logger.trace("Returning reader for: {}\n{}", key, getStatus());
        }
        super.returnObject(key, obj);
    }
    
    /**
     * Provides a snapshot of the pool's current performance metrics.
     * @return a formatted string of pool statistics
     */
    public String getStatus() {
        return String.format("""
               --- Image Pool Status ---
               Active Objects   : %d
               Idle Objects     : %d
               Total Borrowed   : %d
               Created Count    : %d
               Destroyed Count  : %d
               -------------------------
               """,
                getNumActive(),
                getNumIdle(),
                getBorrowedCount(),
                getCreatedCount(),
                getDestroyedCount());
    }
}
