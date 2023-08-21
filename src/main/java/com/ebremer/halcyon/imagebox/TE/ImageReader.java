package com.ebremer.halcyon.imagebox.TE;

import com.github.benmanes.caffeine.cache.Cache;
import java.awt.image.BufferedImage;

/**
 *
 * @author erich
 */
public interface ImageReader extends AutoCloseable {
          
    public String getFormat();
    public BufferedImage readTile(ImageRegion region, Rectangle preferredsize);
    public ImageMeta getMeta();
    public Cache<TileRequest, Tile> getCache();

}
