package com.ebremer.halcyon.lib;

import com.ebremer.halcyon.filereaders.FileReader;
import java.awt.image.BufferedImage;
import org.apache.jena.rdf.model.Model;

/**
 *
 * @author erich
 */
public interface ImageReader extends FileReader {          
    public BufferedImage readTile(ImageRegion region, Rectangle preferredsize);
    public Model readTileMeta(ImageRegion region, Rectangle preferredsize);
    public ImageMeta getImageMeta();
}
