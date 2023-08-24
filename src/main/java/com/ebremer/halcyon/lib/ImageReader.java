package com.ebremer.halcyon.lib;

import com.ebremer.halcyon.filereaders.FileReader;
import java.awt.image.BufferedImage;

/**
 *
 * @author erich
 */
public interface ImageReader extends FileReader {
          
    public BufferedImage readTile(ImageRegion region, Rectangle preferredsize);
    public ImageMeta getImageMeta();

}
