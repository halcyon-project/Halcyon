package com.ebremer.halcyon.server;

import com.ebremer.halcyon.imagebox.TE.Tile;
import com.ebremer.halcyon.imagebox.TE.TileRequest;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.Future;

/**
 *
 * @author erich
 */
public class CacheService implements Service {
    
    private static Cache<TileRequest, Future<Tile>> cache;
    
    public CacheService() {
        System.out.println("Starting Cache Service...");
        cache = Caffeine.newBuilder()
            .recordStats()
            .maximumSize(10000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();
    }

    @Override
    public String getName() {
        return "CacheService";
    }
    
    public static Cache<TileRequest, Future<Tile>> getCache() {
        return cache;
    }
    
}
