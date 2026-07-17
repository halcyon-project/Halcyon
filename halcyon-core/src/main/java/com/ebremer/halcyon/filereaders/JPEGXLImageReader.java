package com.ebremer.halcyon.filereaders;

import com.ebremer.cygnus.jpegxl.imageio.JXLImageReader;
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
 * A {@link ImageReader} for JPEG XL images ({@code .jxl}), backed by the pure-Java
 * {@code com.ebremer:jpegxl} ImageIO plug-in.
 * <p>
 * A JPEG XL file is a single image rather than a pyramid, so the {@link ImageMeta}
 * has one scale. Region reads go through {@link ImageReadParam} as in
 * {@link TiffImageReader}.
 *
 * @author erich
 */
public class JPEGXLImageReader extends AbstractImageReader {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(JPEGXLImageReader.class);
    private static final int METAVERSION = 0;
    private final JXLImageReader reader;
    private final ImageInputStream input;
    private final ImageMeta meta;
    private final URI uri;
    private final URI base;
    private final long sizeInBytes;

    public JPEGXLImageReader(URI uri, URI base) throws IOException {
        logger.info("JPEGXLImageReader(URI uri, URI base) {} {}", uri, base);
        this.uri = uri;
        this.base = base;
        File file = new File(uri);
        sizeInBytes = file.length();
        input = ImageIO.createImageInputStream(file);
        JXLImageReader ir = null;
        Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName("jxl");
        while (readers.hasNext()) {
            javax.imageio.ImageReader candidate = readers.next();
            logger.info("Reader --> {}", candidate.getClass().getCanonicalName());
            if (candidate instanceof JXLImageReader jxl) {
                ir = jxl;
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
        // A single image, so the one scale is the base; without it getBestMatch()
        // would have nothing to return.
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
        return "jxl";
    }

    @Override
    public Set<String> getSupportedFormats() {
        Set<String> set = new HashSet<>();
        set.add("jxl");
        return set;
    }

    private BufferedImage readTile(ImageRegion r, int series) {
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceRegion(new Rectangle(r.getX(), r.getY(), r.getWidth(), r.getHeight()));
        try {
            return reader.read(series, param);
        } catch (IOException ex) {
            Logger.getLogger(JPEGXLImageReader.class.getName()).log(Level.SEVERE, null, ex);
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
        m.createResource(URITools.fix(base))
            .addProperty(LWS.representation, bnode)
            .addLiteral(HAL.filemetaversion, m.createTypedLiteral( METAVERSION, XSD.integer.getURI()))
            .addLiteral(EXIF.width, m.createTypedLiteral(meta.getWidth(), XSD.integer.getURI()))
            .addLiteral(EXIF.height, m.createTypedLiteral(meta.getHeight(), XSD.integer.getURI()))
            .addProperty(RDF.type, LWS.DataResource)
            .addProperty(RDF.type, SchemaDO.ImageObject);
        bnode
            .addProperty(LWS.mediaType, "image/jxl")
            .addLiteral(LWS.sizeInBytes, sizeInBytes);
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
        File file = new File("D:\\HalcyonStorage\\jxl\\sample.jxl");
        try (JPEGXLImageReader reader = new JPEGXLImageReader(file.toURI(), file.toURI())) {
            logger.debug("{}", reader.getImageMeta());
            RDFDataMgr.write(System.out, reader.getMeta(), Lang.TURTLE);
        } catch (Exception ex) {
            Logger.getLogger(JPEGXLImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
