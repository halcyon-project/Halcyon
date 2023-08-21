package com.ebremer.halcyon.imagebox.TE;

import com.ebremer.halcyon.server.CacheService;
import com.ebremer.halcyon.utils.ImageTools;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author erich
 */
public class TileRequest implements Callable<Tile> {
    private final URI uri;
    private final ImageRegion region;
    private final Rectangle preferredsize;
    private final boolean cachethis;
    //private static final ConcurrentHashMap<TileRequest,Tile> queue = new ConcurrentHashMap<>();
    
    private TileRequest(URI uri, ImageRegion region, Rectangle preferredsize, boolean cachethis) {
        this.uri = uri;
        this.region = region;
        this.preferredsize = preferredsize;
        this.cachethis = cachethis;
    }
    
    public boolean isCacheable() {
        return cachethis;
    }
    
    public ImageRegion getRegion() {
        return region;
    }
    
    public Rectangle getPreferredSize() {
        return preferredsize;
    }
    
    public static TileRequest genTileRequest(URI uri, ImageRegion region, Rectangle preferredsize, boolean cachethis) {
        int a = preferredsize.width();
        int b = preferredsize.height();
        if (a>0) {
            if (b>0) {
                return new TileRequest(uri, region, preferredsize, cachethis);
            } else {
                double s = (double) region.getWidth()/ (double) a;
                b = (int) Math.round((double) region.getHeight() / s);
                return new TileRequest(uri, region, new Rectangle(a,b), cachethis);
            }
        } else {
            if (b>0) {
                double s = (double) region.getHeight()/ (double) b;
                a = (int) Math.round((double) region.getWidth() / s);
                return new TileRequest(uri, region, new Rectangle(a,b), cachethis);
            } else {
                return new TileRequest(uri, region, new Rectangle(region.getWidth(),region.getHeight()), cachethis);
            }                 
        }
    }

    @Override
    public Tile call() {
        try {
            ImageReader reader = ImageReaderPool.getPool().borrowObject(uri);
            BufferedImage bi = reader.readTile(region, preferredsize);
            bi = ImageTools.ScaleBufferedImage(bi,preferredsize);
            Tile tile = new Tile(this,bi);
            ImageReaderPool.getPool().returnObject(uri, reader);
            return tile;
        } catch (Exception ex) {
            Logger.getLogger(TileRequest.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    @Override
    public int hashCode() {
	int prime = 31;
	int result = uri.hashCode();
	result = prime * result + region.hashCode();
        result = prime * result + preferredsize.hashCode();
	return result;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
	if (obj == null) {
            return false;
        }
	if (getClass() != obj.getClass()) {
            return false;
        }
	TileRequest other = (TileRequest) obj;
        if (!uri.equals(other.uri))
            return false;
        if (!region.equals(other.getRegion()))
            return false;
        Rectangle r = other.getPreferredSize();
	return preferredsize.equals(r);
    }
}
