package com.ebremer.halcyon.filereaders;

import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.Rectangle;
import java.awt.image.BufferedImage;
import java.net.URI;

/**
 *
 * @author erich
 */
public class BeakGraphFeatureReader implements FeatureReader {
    private final URI uri;

    public BeakGraphFeatureReader(URI uri) {
        this.uri = uri;
    }

    @Override
    public BufferedImage readTile(ImageRegion region, Rectangle preferredsize) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    

    
}
