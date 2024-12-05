package com.ebremer.halcyon.services;

import com.ebremer.halcyon.lib.Tile;
import com.ebremer.halcyon.lib.TileRequest;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheService implements Service {
    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static class CacheHolder {
        static final Cache<TileRequest, Future<Tile>> INSTANCE = Caffeine.newBuilder()
            .recordStats() // Required for .stats() to work
            .maximumSize(5000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                logger.debug("Tile removed from cache: {} (Reason: {})", key, cause);
            })
            .build();
    }

    public CacheService() {
        logger.info("Initializing Cache Service...");
        //startStatsReporting();
    }

    private void startStatsReporting() {
        scheduler.scheduleAtFixedRate(() -> {
            var stats = getCache().stats();
            logger.debug("Cache Stats - Hit Rate: {}%, Misses: {}, Evictions: {}, Size: {}", 
                String.format("%.2f", stats.hitRate() * 100),
                stats.missCount(),
                stats.evictionCount(),
                getCache().estimatedSize()
            );
        }, 0, 3, TimeUnit.SECONDS);
    }

    @Override
    public String getName() {
        return "CacheService";
    }

    public static Cache<TileRequest, Future<Tile>> getCache() {
        return CacheHolder.INSTANCE;
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
}
