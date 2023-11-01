package com.ebremer.halcyon.filereaders;

import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.Rectangle;
import java.awt.image.BufferedImage;

/**
 *
 * @author erich
 */
public interface FeatureReader {

    public BufferedImage readTile(ImageRegion region, Rectangle preferredsize);
    
}
