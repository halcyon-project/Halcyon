package com.ebremer.halcyon.imagebox.TE;

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
public class TileRequestEngine {
    private final ExecutorService executor;
    private final URI uri;
    
    public TileRequestEngine(URI uri) throws Exception {
        this.uri = uri;
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    public Future<Tile> getFutureTile(ImageRegion region, Rectangle preferredsize, boolean keep) {
        return executor.submit(TileRequest.genTileRequest(uri,region,preferredsize, keep));
    }
    
    public Future<Tile> getFutureTile(TileRequest tr) {
        return executor.submit(tr);
    }
    
    public Tile getTile(ImageRegion region, Rectangle preferredsize, boolean keep) {
        //long begin = System.nanoTime();
        TileRequest tr = TileRequest.genTileRequest(uri,region,preferredsize, keep);
        try {
            return executor.submit(tr).get(1000, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Logger.getLogger(TileRequestEngine.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            Logger.getLogger(TileRequestEngine.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TimeoutException ex) {
            Logger.getLogger(TileRequestEngine.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
