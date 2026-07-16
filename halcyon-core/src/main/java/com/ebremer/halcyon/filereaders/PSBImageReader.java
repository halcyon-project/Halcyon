package com.ebremer.halcyon.filereaders;

import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.URITools;
import com.ebremer.halcyon.utils.ImageTools;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import com.ebremer.ns.LWS;
import com.twelvemonkeys.imageio.plugins.psd.PSDImageReader;
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

public class PSBImageReader extends AbstractImageReader {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(PSBImageReader.class);
    private javax.imageio.ImageReader reader;
    /** H11: the stream the reader reads from — was a local, so nothing closed it. */
    private ImageInputStream input;
    private final ImageMeta meta;
    private final URI uri;
    private final URI base;
    private static final int METAVERSION = 0;
    private long sizeInBytes;

    /**
     * H11: this class was entirely non-functional.
     * <p>
     * The constructor declared {@code PSDImageReader reader = ...}, a LOCAL that
     * SHADOWED the field of the same name. Everything here then used the local,
     * so construction appeared to work — {@code meta} was built fine — but the
     * FIELD was left null, and every later call NPE'd on it: {@code readTile}
     * (via {@code reader.getDefaultReadParam()}) and {@code close()} (via
     * {@code reader.dispose()}). It is assigned properly now.
     */
    public PSBImageReader(URI uri, URI base) throws IOException {
        logger.info("PSBImageReader(URI uri, URI base) {} {}", uri, base);
        this.uri = uri;
        this.base = base;
        File file = new File(uri);
        sizeInBytes = file.length();
        ImageInputStream in = ImageIO.createImageInputStream(file);
        if (in == null) {
            throw new IOException("No ImageInputStream for: " + file);
        }
        javax.imageio.ImageReader ir = null;
        try {
            ir = psdReader(file);
            ir.setInput(in);
            ImageMeta.Builder builder = ImageMeta.Builder.getBuilder(0, ir.getWidth(0), ir.getHeight(0))
                .setTileSizeX(ir.getTileWidth(0))
                .setTileSizeY(ir.getTileHeight(0));
            // H12: start at 0, not 1 — include the base (scale 1) so getBestMatch()
            // has something to return and can select full resolution, as the other
            // readers do. A single-image PSD/PSB otherwise yielded ZERO scales and
            // every read 500'd on scales.get(-1).
            for (int s=0; s<ir.getNumImages(true); s++) {
                builder.addScale(s, ir.getWidth(s), ir.getHeight(s));
            }
            meta = builder.build();
            // Assign the FIELDS — this is the whole bug.
            this.reader = ir;
            this.input = in;
        } catch (IOException | RuntimeException ex) {
            // close() can never run for an object that was never constructed, so
            // clean up here or the handle leaks on every failed open.
            if (ir != null) {
                ir.dispose();
            }
            closeQuietly(in);
            throw ex;
        }
    }

    /**
     * The TwelveMonkeys PSD reader (it handles PSB — Photoshop's large-document
     * format — through the same plugin), or an exception.
     * <p>
     * H11: this was a bare {@code (PSDImageReader) readers.next()} — no
     * {@code hasNext()} guard, so a realm with no PSD plugin got a
     * NoSuchElementException, and an unchecked cast that would CCE if the first
     * "psd" reader were some other implementation. The {@code if (reader == null)}
     * check that followed was dead: {@code next()} throws, it never returns null.
     */
    private static javax.imageio.ImageReader psdReader(File file) throws IOException {
        Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName("psd");
        while (readers.hasNext()) {
            javax.imageio.ImageReader candidate = readers.next();
            logger.debug("Reader --> {}", candidate.getClass().getCanonicalName());
            if (candidate instanceof PSDImageReader psd) {
                return psd;
            }
            // Not the one we want: ImageIO handed us a live instance, so let it go.
            candidate.dispose();
        }
        logger.error("No PSD/PSB reader for: {}", file);
        throw new IOException("No TwelveMonkeys PSD reader available for: " + file);
    }

    private static void closeQuietly(ImageInputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ex) {
                logger.debug("closing input : {}", ex.getMessage());
            }
        }
    }

    @Override
    public int getMetaVersion() {
        return METAVERSION;
    }

    @Override
    public String getFormat() {
        return "psd";
    }
    
    @Override
    public Set<String> getSupportedFormats() {
        Set<String> set = new HashSet<>();
        set.add("psb");
        return set;
    }
    
    private BufferedImage readTile(ImageRegion r, int series) {
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceRegion(new Rectangle(r.getX(), r.getY(), r.getWidth(), r.getHeight()));
        try {
            return reader.read(series, param);
        } catch (IOException ex) {
            Logger.getLogger(PSBImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     * H11: dispose the reader AND close its stream — {@code dispose()} alone
     * leaves the ImageInputStream (and its file handle) open, so the pool leaked
     * one FD per evicted reader (same defect as H10). Idempotent, and no longer
     * NPEs on the null field. Matches SVS/NDPI/JPEG2000/JPEGXL.
     */
    @Override
    public void close() {
        if (reader != null) {
            reader.dispose();
            reader = null;
        }
        closeQuietly(input);
        input = null;
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
            .addProperty(LWS.mediaType, "image/vnd.adobe.photoshop")
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
    
    public static void main(String[] args) throws IOException {
        File file = new File("D:\\HalcyonStorage\\nasa\\eso1719a.psb");
        PSBImageReader reader = new PSBImageReader(file.toURI(), file.toURI());
        RDFDataMgr.write(System.out, reader.getMeta(), Lang.TURTLE);
    }
}
