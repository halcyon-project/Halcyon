package com.ebremer.halcyon.lib;

import com.ebremer.halcyon.filereaders.ImageReader;
import com.ebremer.halcyon.server.utils.ImageReaderPool;
import com.ebremer.halcyon.utils.ImageTools;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.concurrent.Callable;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TileRequest – effectively immutable for safe use as a Caffeine cache key.
 *
 * @author erich
 */
public class TileRequest implements Callable<Tile> {

    private static final Logger logger = LoggerFactory.getLogger(TileRequest.class);

    private final URI uri;
    private final ImageRegion region;
    private final Rectangle preferredsize;
    private final boolean cachethis;
    private final boolean retrieveBufferedImage;
    private final boolean retrieveMeta;
    private final boolean aspectratio;

    /**
     * Private constructor – called only after normalization.
     */
    private TileRequest(URI uri, ImageRegion region, Rectangle preferredsize,
                        boolean cachethis, boolean retrieveBufferedImage,
                        boolean retrieveMeta, boolean aspectratio) {
        this.uri = uri;
        this.region = region;
        this.preferredsize = preferredsize;
        this.cachethis = cachethis;
        this.retrieveBufferedImage = retrieveBufferedImage;
        this.retrieveMeta = retrieveMeta;
        this.aspectratio = aspectratio;
    }

    /**
     * Normalizes the preferred dimensions (fills in missing width/height)
     * before the object is ever placed in the cache.
     */
    private static Rectangle normalizePreferredSize(ImageRegion region, Rectangle requested) {
        int w = requested.width();
        int h = requested.height();

        if (w > 0 && h <= 0) {
            // Scale height based on width
            double ratio = (double) region.getWidth() / w;
            h = (int) Math.round(region.getHeight() / ratio);
            return new Rectangle(w, h);
        } else if (h > 0 && w <= 0) {
            // Scale width based on height
            double ratio = (double) region.getHeight() / h;
            w = (int) Math.round(region.getWidth() / ratio);
            return new Rectangle(w, h);
        } else if (w <= 0 && h <= 0) {
            // Fallback: use original region dimensions
            return new Rectangle(region.getWidth(), region.getHeight());
        }
        // Both dimensions supplied – use as-is
        return requested;
    }

    /**
     * Factory method – always returns a fully normalized, immutable TileRequest.
     */
    public static TileRequest genTileRequest(URI uri, ImageRegion region, Rectangle preferredsize,
            boolean cachethis, boolean retrieveBufferedImage, boolean retrieveMeta, boolean aspectratio) {

        Rectangle normalized = normalizePreferredSize(region, preferredsize);
        return new TileRequest(uri, region, normalized, cachethis,
                               retrieveBufferedImage, retrieveMeta, aspectratio);
    }

    // ------------------------------------------------------------------------
    // Public getters
    // ------------------------------------------------------------------------
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

    // ------------------------------------------------------------------------
    // Data retrieval methods (unchanged)
    // ------------------------------------------------------------------------
    public BufferedImage getBufferedImage(boolean aspectratio) {
        ImageReader reader = null;
        try {
            reader = ImageReaderPool.getPool().borrowObject(uri);
            BufferedImage bi = reader.readTile(region, preferredsize);
            bi = ImageTools.ScaleBufferedImage(bi, preferredsize, aspectratio);
            return bi;
        } catch (Exception ex) {
            logger.error("getBufferedImage {}", ex.getMessage());
        } finally {
            if (reader != null) {
                try {
                    ImageReaderPool.getPool().returnObject(uri, reader);
                } catch (Exception e) {
                    logger.error("Pool Return Error: {}", e.getMessage());
                }
            }
        }
        return null;
    }

    public Model getMeta() {
        ImageReader reader = null;
        try {
            reader = ImageReaderPool.getPool().borrowObject(uri);
            return reader.readTileMeta(region, preferredsize);
        } catch (Exception ex) {
            logger.error(ex.getMessage());
        } finally {
            if (reader != null) {
                try {
                    ImageReaderPool.getPool().returnObject(uri, reader);
                } catch (Exception e) {
                    logger.error("Pool Return Error: {}", e.getMessage());
                }
            }
        }
        return ModelFactory.createDefaultModel();
    }

    // ------------------------------------------------------------------------
    // Callable implementation – no mutation
    // ------------------------------------------------------------------------
    @Override
    public Tile call() {
        logger.trace("Processing TileRequest for URI: {}", uri);

        Tile tile = new Tile(this);

        try {
            if (retrieveBufferedImage) {
                BufferedImage bi = getBufferedImage(aspectratio);
                if (bi != null) {
                    tile.setBufferedImage(bi);
                } else {
                    logger.error("Failed to retrieve BufferedImage for {}", uri);
                }
            }
            if (retrieveMeta) {
                Model meta = getMeta();
                tile.setMeta(meta);
            }
        } catch (Exception e) {
            logger.error("Unexpected error during TileRequest execution", e);
        }
        return tile;
    }

    // ------------------------------------------------------------------------
    // Consistent equals / hashCode contract (required for Caffeine cache)
    // ------------------------------------------------------------------------
    @Override
    public int hashCode() {
        int result = uri.hashCode();
        result = 31 * result + Boolean.hashCode(aspectratio);
        result = 31 * result + region.hashCode();
        result = 31 * result + preferredsize.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        TileRequest other = (TileRequest) obj;

        return uri.equals(other.uri) &&
               aspectratio == other.aspectratio &&
               region.equals(other.region) &&
               preferredsize.equals(other.preferredsize);
    }
}
