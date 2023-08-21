package com.ebremer.halcyon.imagebox.TE;

import java.awt.image.BufferedImage;

/**
 *
 * @author erich
 */
public interface ImageReader extends AutoCloseable {
          
    public String getFormat();
    public BufferedImage readTile(ImageRegion region, Rectangle preferredsize);
    public ImageMeta getMeta();

}
