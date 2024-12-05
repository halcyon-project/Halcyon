package com.ebremer.halcyon.lib;

import com.ebremer.halcyon.services.CacheService;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TileRequestEngine {
    private static final Logger logger = LoggerFactory.getLogger(TileRequestEngine.class);
    private final ExecutorService executor;
    private final Cache<TileRequest, Future<Tile>> cache;

    private TileRequestEngine() {
        logger.info("Initializing Global TileRequestEngine with Virtual Thread Executor...");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.cache = CacheService.getCache();
    }

    private static class Holder {
        private static final TileRequestEngine INSTANCE = new TileRequestEngine();
    }

    public static TileRequestEngine getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Retrieves a Future for the requested tile.
     * Uses an atomic compute pattern to ensure only one thread generates a tile for a specific key.
     * @param tr
     * @return 
     */
    public Future<Tile> getFutureTile(TileRequest tr) {
        if (!tr.isCacheable()) {
            return executor.submit(tr);
        }

        // We wrap the submission in a pattern that cleans the cache if the generation fails.
        return cache.get(tr, key -> executor.submit(() -> {
            try {
                logger.trace("Cache miss. Generating tile for: {}", key.getRegion());
                return key.call();
            } catch (Exception e) {
                logger.error("Tile generation failed. Evicting poisoned key from cache: {}", key.getRegion());
                cache.invalidate(key); // Ensure we don't cache a permanent failure
                throw e;
            }
        }));
    }

    /**
     * Blocking call to retrieve a Tile with a timeout.
     * @param tr
     * @return 
     */
    public Tile getTile(TileRequest tr) {
        try {
            return getFutureTile(tr).get(60, TimeUnit.SECONDS);
        } catch (Exception ex) {
            logger.error("Tile Request timed out or failed for {}: {}", tr.getRegion(), ex.getMessage());
            // Invalidate on failure here as an extra safety measure
            if (tr.isCacheable()) {
                cache.invalidate(tr);
            }
            return null;
        }
    }

    /**
     * Graceful shutdown of the virtual thread executor.
     */
    public void shutdown() {
        logger.info("Shutting down TileRequestEngine...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
