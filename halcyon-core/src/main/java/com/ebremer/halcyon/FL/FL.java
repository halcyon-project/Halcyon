package com.ebremer.halcyon.FL;

import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.raptor.Objects.Scale;
import java.awt.image.BufferedImage;
import java.util.List;
import org.apache.jena.rdf.model.Model;

/**
 *
 * @author erich
 */
public interface FL {
    
    abstract public void close();
    abstract public int getTileSizeX();
    abstract public int getTileSizeY();
    abstract public int getWidth();
    abstract public int getHeight();
    abstract public List<Scale> getScales();
    abstract public Model getManifest();
    abstract public BufferedImage readTile(ImageRegion r, int series);
    abstract public Model readTileMeta(ImageRegion r, int series);
    abstract public Model LoadExtendedManifest();
    abstract public String GetIIIFImageInfo(String base);
}
