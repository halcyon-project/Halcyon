package com.ebremer.halcyon.lib;

import com.ebremer.halcyon.server.CacheService;
import com.github.benmanes.caffeine.cache.Cache;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author erich
 */
public class TileRequestEngine implements AutoCloseable {
    private final ExecutorService executor;
    private final URI uri;
    private final Cache<TileRequest, Future<Tile>> cache;
    
    public TileRequestEngine(URI uri) throws Exception {
        this.uri = uri;
        executor = Executors.newVirtualThreadPerTaskExecutor();
        cache = CacheService.getCache();
    }
    
    public Future<Tile> getFutureTile(ImageRegion region, Rectangle preferredsize, boolean keep) {
        return getFutureTile(TileRequest.genTileRequest(uri,region,preferredsize, keep));
    }
    
    public Future<Tile> getFutureTile(TileRequest tr) {
        Future<Tile> future = cache.getIfPresent(tr);
        if (future == null) {
            future = executor.submit(tr);
            if (tr.isCacheable()) {
                cache.put(tr, future);
            }
        }
        return future;
    }
    
    public Tile getTile(ImageRegion region, Rectangle preferredsize, boolean keep) {
        TileRequest tr = TileRequest.genTileRequest(uri, region, preferredsize, keep);
        try {
            return getFutureTile(tr).get(1000, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Logger.getLogger(TileRequestEngine.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            Logger.getLogger(TileRequestEngine.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TimeoutException ex) {
            Logger.getLogger(TileRequestEngine.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public void close() throws Exception {
        executor.close();
    }
}
