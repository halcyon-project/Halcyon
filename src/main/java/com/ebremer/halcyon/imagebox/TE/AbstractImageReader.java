package com.ebremer.halcyon.imagebox.TE;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.URI;
import java.time.Duration;

/**
 *
 * @author erich
 */
public abstract class AbstractImageReader implements ImageReader {
    
    private static Cache<TileRequest, Tile> cache;
    
    public AbstractImageReader(URI uri) {
        cache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();
    }
    
    @Override
    public Cache<TileRequest, Tile> getCache() {
        return cache;
    }

}
