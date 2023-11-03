package com.ebremer.halcyon.filereaders;

import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.Rectangle;
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
