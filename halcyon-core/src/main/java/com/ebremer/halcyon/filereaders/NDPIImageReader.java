package com.ebremer.halcyon.filereaders;

import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.URITools;
import com.ebremer.halcyon.utils.ImageTools;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import com.ebremer.ns.LWS;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.stream.ImageInputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.LoggerFactory;

/**
 * A {@link ImageReader} for Hamamatsu NDPI whole-slide images, backed by the
 * {@code com.ebremer:ndpi} ImageIO plug-in.
 * <p>
 * Like {@link SVSImageReader}, the plug-in presents the slide's pyramid as
 * ImageIO image indices and reads regions through {@link ImageReadParam}, so
 * this reader is the same shape as {@link TiffImageReader}. An NDPI file opens
 * like a TIFF and is not one — its offsets are 64 bits smuggled into a 32-bit
 * container, and each level is a single JPEG larger than a JPEG is allowed to be
 * — so a TIFF reader cannot stand in for the plug-in here.
 * <p>
 * A level's tile grid is the grid of its JPEG's restart intervals: wide, and one
 * MCU tall. Reading whole tiles is therefore not the cheap way round the slide
 * that it is in a tiled format, which is why this reader only ever asks for the
 * region it wants.
 *
 * @author erich
 */
public class NDPIImageReader extends AbstractImageReader {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(NDPIImageReader.class);
    private static final int METAVERSION = 0;
    private final com.ebremer.cygnus.ndpi.imageio.NDPIImageReader reader;
    private final ImageInputStream input;
    private final ImageMeta meta;
    private final URI uri;
    private final URI base;
    private final long sizeInBytes;

    public NDPIImageReader(URI uri, URI base) throws IOException {
        logger.info("NDPIImageReader(URI uri, URI base) {} {}", uri, base);
        this.uri = uri;
        this.base = base;
        File file = new File(uri);
        sizeInBytes = file.length();
        input = ImageIO.createImageInputStream(file);
        com.ebremer.cygnus.ndpi.imageio.NDPIImageReader ir = null;
        Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName("ndpi");
        while (readers.hasNext()) {
            javax.imageio.ImageReader candidate = readers.next();
            logger.info("Reader --> {}", candidate.getClass().getCanonicalName());
            if (candidate instanceof com.ebremer.cygnus.ndpi.imageio.NDPIImageReader ndpi) {
                ir = ndpi;
            }
        }
        if (ir == null) {
            logger.error("No reader for: {}", file);
            throw new IllegalArgumentException("No reader for: " + file);
        }
        reader = ir;
        reader.setInput(input);
        ImageMeta.Builder builder = ImageMeta.Builder.getBuilder(0, reader.getWidth(0), reader.getHeight(0))
            .setTileSizeX(reader.getTileWidth(0))
            .setTileSizeY(reader.getTileHeight(0));
        OptionalDouble magnification = reader.getMagnification();
        if (magnification.isPresent()) {
            builder.setMagnification(magnification.getAsDouble());
        }
        // Include the base (scale 1) as well as the reduced levels so that
        // getBestMatch() can select full resolution, as DICOMImageReader does.
        for (int s = 0; s < reader.getNumImages(true); s++) {
            builder.addScale(s, reader.getWidth(s), reader.getHeight(s));
        }
        meta = builder.build();
    }

    @Override
    public int getMetaVersion() {
        return METAVERSION;
    }

    @Override
    public String getFormat() {
        return "ndpi";
    }

    @Override
    public Set<String> getSupportedFormats() {
        Set<String> set = new HashSet<>();
        set.add("ndpi");
        return set;
    }

    private BufferedImage readTile(ImageRegion r, int series) {
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceRegion(new Rectangle(r.getX(), r.getY(), r.getWidth(), r.getHeight()));
        try {
            return reader.read(series, param);
        } catch (IOException ex) {
            Logger.getLogger(NDPIImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public BufferedImage readTile(ImageRegion region, com.ebremer.halcyon.lib.Rectangle preferredsize) {
        ImageMeta.ImageScale scale = meta.getBestMatch(Math.max((double) region.getWidth()/(double) preferredsize.width(),(double) region.getHeight()/ (double) preferredsize.height()));
        return ImageTools.ScaleBufferedImage(readTile(scale.Validate(region.scaleRegion(scale.scale())),scale.series()),preferredsize, true);
    }

    @Override
    public ImageMeta getImageMeta() {
        return meta;
    }

    @Override
    public Model getMeta(URI xuri) {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("exif", EXIF.NS);
        m.setNsPrefix("sdo", SchemaDO.NS);
        m.setNsPrefix("hal", HAL.NS);
        m.setNsPrefix("lws", LWS.NS);
        m.setNsPrefix("xsd", XSD.getURI());
        Resource bnode = m.createResource();
        Resource root = m.createResource(URITools.fix(base))
            .addProperty(LWS.representation, bnode)
            .addLiteral(HAL.filemetaversion, m.createTypedLiteral( METAVERSION, XSD.integer.getURI()))
            .addLiteral(EXIF.width, m.createTypedLiteral(meta.getWidth(), XSD.integer.getURI()))
            .addLiteral(EXIF.height, m.createTypedLiteral(meta.getHeight(), XSD.integer.getURI()))
            .addProperty(RDF.type, LWS.DataResource)
            .addProperty(RDF.type, SchemaDO.ImageObject);
        bnode
            .addProperty(LWS.mediaType, "image/x-hamamatsu-ndpi")
            .addLiteral(LWS.sizeInBytes, sizeInBytes);
        try {
            // NDPI carries the resolution in the TIFF tags rather than in a description,
            // and the scanner's own settings in a private property map.
            OptionalDouble mppx = reader.getMicronsPerPixelX();
            if (mppx.isPresent()) {
                root.addLiteral(HAL.mppx, mppx.getAsDouble());
            }
            OptionalDouble mppy = reader.getMicronsPerPixelY();
            if (mppy.isPresent()) {
                root.addLiteral(HAL.mppy, mppy.getAsDouble());
            }
        } catch (IOException ex) {
            Logger.getLogger(NDPIImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }
        return m;
    }

    @Override
    public Model getMeta() {
        return getMeta(uri);
    }

    @Override
    public Model readTileMeta(ImageRegion region, com.ebremer.halcyon.lib.Rectangle preferredsize) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void close() {
        reader.dispose();
        try {
            input.close();
        } catch (IOException ex) {
            logger.debug("closing {} : {}", uri, ex.getMessage());
        }
    }

    public static void main(String[] args) throws IOException {
        File file = new File("E:\\images\\Hamamatsu-NDPI\\openslide\\CMU-1\\CMU-1.ndpi");
        try (NDPIImageReader reader = new NDPIImageReader(file.toURI(), file.toURI())) {
            logger.debug("{}", reader.getImageMeta());
            RDFDataMgr.write(System.out, reader.getMeta(), Lang.TURTLE);
        } catch (Exception ex) {
            Logger.getLogger(NDPIImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
