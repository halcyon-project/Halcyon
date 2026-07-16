package com.ebremer.halcyon.lib;

import com.ebremer.halcyon.utils.HalJsonLD;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;

/**
 *
 * @author erich
 */
/**
 * The result of a {@link TileRequest}.
 * <p>
 * M13: instances are shared between threads — {@code TileRequestEngine} caches the
 * {@code Future<Tile>}, so every concurrent request for the same region gets THIS
 * object. It is therefore immutable in its image, and its lazy metadata is
 * synchronized. It previously had neither property: {@code bi} was a plain
 * non-final, non-volatile field written by setters after publication, so readers
 * could see a stale null (or a half-built image) with no happens-before edge.
 */
public class Tile {
    public static enum TileType {RDF, BUFFEREDIMAGE};
    private final TileRequest tilerequest;
    /** M13: final — set once by {@link TileRequest#call()}, safely published. */
    private final BufferedImage bi;
    /** M13: lazily built for meta-only formats; guarded by {@code this}. */
    private Model meta;

    public TileRequest getTileRequest() {
        return this.tilerequest;
    }

    public Tile(TileRequest tilerequest, BufferedImage bi) {
        this(tilerequest, bi, null);
    }

    public Tile(TileRequest tilerequest, BufferedImage bi, Model meta) {
        this.tilerequest = tilerequest;
        this.bi = bi;
        this.meta = meta;
    }

    /**
     * M13: no longer re-decodes.
     * <p>
     * This used to be {@code if (bi==null) bi = tilerequest.getBufferedImage(...)},
     * which was three bugs at once. It ran a full decode <em>on the caller's thread</em>
     * — defeating the executor and the cache the tile had just been fetched from; it
     * did so on EVERY access to a cached failed tile, so a broken region became a
     * permanent CPU sink rather than an error; and it raced, because concurrent
     * readers of this shared object could each start their own decode and publish
     * {@code bi} through a non-volatile field. {@link TileRequest#call()} now throws
     * rather than handing back an imageless tile, so a cached Tile always has the
     * image it was asked for, and this is a plain read.
     */
    public BufferedImage getBufferedImage() {
        return bi;
    }

    /** M13: build-once, guarded — the meta counterpart of the image race above. */
    private synchronized Model meta() {
        if (meta == null) {
            meta = tilerequest.getMeta();
        }
        return meta;
    }

    public String getMeta(RDFFormat format) {
        Model meta = meta();
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            RDFDataMgr.write(bos, meta, format);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }
    
    public void getMeta(RDFFormat format, OutputStream out) {
        Model meta = meta();
        if (format.equals(RDFFormat.JSONLD11_PRETTY)) {
            HalJsonLD.GetPolygons(meta, out);
        } else {
            RDFDataMgr.write(out, meta, format);
        }
    }

    public boolean Write(Path path) {
        path.toFile().mkdirs();
        Path file = Path.of(path.toString(), "tile-"+tilerequest.getRegion().getX()+"-"+tilerequest.getRegion().getY()+"-"+tilerequest.getRegion().getWidth()+"-"+tilerequest.getRegion().getHeight()+".png");
        try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
            return ImageIO.write(getBufferedImage(), "png", fos);
        } catch (IOException ex) {
            Logger.getLogger(Tile.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }
}
