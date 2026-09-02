package com.ebremer.halcyon.lib;

import com.ebremer.halcyon.filereaders.ImageReader;
import com.ebremer.halcyon.server.utils.ImageReaderPool;
import com.ebremer.halcyon.utils.ImageTools;
import java.awt.image.BufferedImage;
import java.io.IOException;
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
    /**
     * M13: this must THROW when it cannot produce what was asked for.
     * <p>
     * It used to log a failed decode and return a {@code Tile} with a null image,
     * and swallow every exception besides. Two consequences, both bad:
     * <ul>
     *   <li>{@code TileRequestEngine.getFutureTile} wraps this call in
     *       {@code catch (Exception e) { cache.invalidate(key); throw e; }} — the
     *       "evict poisoned key" path. Since this method never threw, that catch was
     *       <em>unreachable</em> and the failed Tile was cached like a success. The
     *       cache is {@code expireAfterAccess(10 min)}, so every retry RESET the
     *       clock: a tile that failed once could stay poisoned indefinitely, for as
     *       long as anyone kept asking for it.</li>
     *   <li>A swallowed exception lost the reason. The caller saw only a null image.</li>
     * </ul>
     * Throwing lets the wrapper evict the key and surfaces the real cause to
     * {@code ImageServer} as an ExecutionException, which answers 500 and leaves the
     * next request free to try again.
     * <p>
     * A null image is only an error when one was actually requested — a meta-only
     * request ({@code retrieveBufferedImage == false}) legitimately has none.
     */
    @Override
    public Tile call() throws Exception {
        logger.trace("Processing TileRequest for URI: {}", uri);
        BufferedImage bi = null;
        Model m = null;
        if (retrieveBufferedImage) {
            bi = getBufferedImage(aspectratio);
            if (bi == null) {
                throw new IOException("Tile decode produced no image for " + uri + " region=" + region);
            }
        }
        if (retrieveMeta) {
            m = getMeta();
        }
        return new Tile(this, bi, m);
    }

    // ------------------------------------------------------------------------
    // Consistent equals / hashCode contract (required for Caffeine cache)
    // ------------------------------------------------------------------------
    /**
     * L9: {@code retrieveBufferedImage} / {@code retrieveMeta} are part of the
     * identity, because they decide WHAT the resulting Tile contains.
     * <p>
     * They were omitted, which was survivable only because the single caller
     * passed them as compile-time constants — every request looked the same. The
     * moment {@code ImageServer} started deriving them from the output format (so
     * that a {@code .ttl} request stops decoding the whole image), {@code
     * /default.jpg} and {@code /default.ttl} for the same uri+region+size became
     * EQUAL keys, and whichever arrived first would win the Caffeine entry: the
     * .jpg caller could be handed a meta-only Tile with a null image and 500, or
     * the .ttl caller silently get the full decode back. Ordering-dependent, so it
     * would have shown up as an intermittent bug long after the change.
     * <p>
     * {@code cachethis} is deliberately NOT included: it governs whether the entry
     * is stored, not what it holds, so two otherwise-identical requests should
     * still share one entry.
     */
    @Override
    public int hashCode() {
        int result = uri.hashCode();
        result = 31 * result + Boolean.hashCode(aspectratio);
        result = 31 * result + region.hashCode();
        result = 31 * result + preferredsize.hashCode();
        result = 31 * result + Boolean.hashCode(retrieveBufferedImage);
        result = 31 * result + Boolean.hashCode(retrieveMeta);
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
               preferredsize.equals(other.preferredsize) &&
               retrieveBufferedImage == other.retrieveBufferedImage &&
               retrieveMeta == other.retrieveMeta;
    }
}
