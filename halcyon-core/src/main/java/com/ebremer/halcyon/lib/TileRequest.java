package com.ebremer.halcyon.lib;

import com.ebremer.halcyon.filereaders.ImageReader;
import com.ebremer.halcyon.server.utils.ImageReaderPool;
import com.ebremer.halcyon.utils.ImageTools;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 *
 * @author erich
 */
public class TileRequest implements Callable<Tile> {
    private final URI uri;
    private final ImageRegion region;
    private Rectangle preferredsize;
    private final boolean cachethis;
    private final boolean retrieveBufferedImage;
    private final boolean retrieveMeta;
    private final boolean aspectratio;
    
    private TileRequest(URI uri, ImageRegion region, Rectangle preferredsize, boolean cachethis, boolean retrieveBufferedImage, boolean retrieveMeta, boolean aspectratio) {
        this.uri = uri;
        this.region = region;
        this.preferredsize = preferredsize;
        this.cachethis = cachethis;
        this.retrieveBufferedImage = retrieveBufferedImage;
        this.retrieveMeta = retrieveMeta;
        this.aspectratio = aspectratio;
    }
    
    public boolean isCacheable() {
        return cachethis;
    }
    
    public boolean MaintainAspectRatio() {
        return aspectratio;
    }
    
    public ImageRegion getRegion() {
        return region;
    }
    
    public Rectangle getPreferredSize() {
        return preferredsize;
    }
    
    public static TileRequest genTileRequest(URI uri, ImageRegion region, Rectangle preferredsize, boolean cachethis, boolean retrieveBufferedImage, boolean retrieveMeta, boolean aspectratio) {
        return new TileRequest(uri, region, preferredsize, cachethis, retrieveBufferedImage, retrieveMeta, aspectratio);
    }
    
    public BufferedImage getBufferedImage(boolean aspectratio) {
        try {
            ImageReader reader = ImageReaderPool.getPool().borrowObject(uri);
            BufferedImage bi   = reader.readTile(region, preferredsize);
            ImageReaderPool.getPool().returnObject(uri, reader);
            bi = ImageTools.ScaleBufferedImage(bi, preferredsize, aspectratio);
            return bi;
        } catch (Exception ex) {
            Logger.getLogger(TileRequest.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public Model getMeta() {
        try {
            ImageReader reader = ImageReaderPool.getPool().borrowObject(uri);
            return reader.readTileMeta(region, preferredsize);
        } catch (Exception ex) {
            Logger.getLogger(TileRequest.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ModelFactory.createDefaultModel();
    }

    @Override
    public Tile call() {
        Tile tile = new Tile(this);
        int a = preferredsize.width();
        int b = preferredsize.height();
        if (a>0) {
            if (b>0) {
            } else {
                double s = (double) region.getWidth()/ (double) a;
                b = (int) Math.round((double) region.getHeight() / s);
                preferredsize =  new Rectangle(a,b);
            }
        } else {
            if (b>0) {
                double s = (double) region.getHeight() / (double) b;
                a = (int) Math.round((double) region.getWidth() / s);
                preferredsize =  new Rectangle(a,b);
            } else {
                preferredsize =  new Rectangle(a,b);
            }                 
        }
        if (retrieveBufferedImage) {
            tile.setBufferedImage(getBufferedImage(aspectratio));
        }
        if (retrieveMeta) {
           tile.setMeta(getMeta());
        }
        return tile;
    }
    
    @Override
    public int hashCode() {
	int prime = 31;
	int result = uri.hashCode();
        result = prime * result + ((Boolean) aspectratio).hashCode();
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
