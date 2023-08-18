
package com.ebremer.halcyon.imagebox.TE;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.time.Duration;
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
    private static Cache<TileRequest, BufferedImage> cache;
    private final boolean cachethis;
    
    private TileRequest(URI uri, ImageRegion region, Rectangle preferredsize, boolean cachethis) {
        this.uri = uri;
        this.region = region;
        this.preferredsize = preferredsize;
        this.cachethis = cachethis;
        cache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();
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
        BufferedImage bi = cache.getIfPresent(this);
        ImageReaderPool pool = ImageReaderPool.getPool();
        try {
            ImageReader reader = pool.borrowObject(uri);
            bi = reader.readTile(region, preferredsize);
            pool.returnObject(uri, reader);
            bi = ImageTools.ScaleBufferedImage(bi,preferredsize);
        } catch (Exception ex) {
            Logger.getLogger(TileRequest.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (cachethis) {
            cache.put(this, bi);
        }
        return new Tile(this, bi);
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
