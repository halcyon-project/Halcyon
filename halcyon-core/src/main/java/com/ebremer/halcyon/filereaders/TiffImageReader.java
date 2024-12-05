package com.ebremer.halcyon.filereaders;

import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.URITools;
import com.ebremer.halcyon.lib.XMP;
import com.ebremer.halcyon.utils.ImageTools;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import com.ebremer.ns.LWS;
import com.twelvemonkeys.imageio.metadata.tiff.Rational;
import com.twelvemonkeys.imageio.metadata.tiff.TIFF;
import com.twelvemonkeys.imageio.plugins.tiff.TIFFImageMetadata;
import com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReader;
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

public class TiffImageReader extends AbstractImageReader {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(TiffImageReader.class);
    private javax.imageio.ImageReader reader;
    private final ImageMeta meta;
    private final URI uri;
    private final URI base;
    private static final int METAVERSION = 0;
    private long sizeInBytes;

    public TiffImageReader(URI uri, URI base) throws IOException {
        logger.info("TiffImageReader(URI uri, URI base) {} {}", uri, base);
        this.uri = uri;
        this.base = base;
        File file = new File(uri);
        sizeInBytes = file.length();
        ImageInputStream input = ImageIO.createImageInputStream(file);
        Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName("tif");
        javax.imageio.ImageReader ir = null;
        while (readers.hasNext()) {            
            ir = readers.next();
            logger.info("Reader --> {}",ir, ir.getClass().toGenericString());
            if ("com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReader".equals(ir.getClass().getCanonicalName())) {
                reader = ir;
            }
        }
        if (ir==null) {
            logger.error("No reader for: {}", file);
            throw new IllegalArgumentException("No reader for: " + file);
        }
        reader.setInput(input);            
        ImageMeta.Builder builder = ImageMeta.Builder.getBuilder(0, reader.getWidth(0), reader.getHeight(0))
            .setTileSizeX(reader.getTileWidth(0))
            .setTileSizeY(reader.getTileHeight(0));
        for (int s=1; s<reader.getNumImages(true); s++) {
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
        return "tif";
    }

    private BufferedImage readTile(ImageRegion r, int series) {
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceRegion(new Rectangle(r.getX(), r.getY(), r.getWidth(), r.getHeight()));
        try {
            return reader.read(series, param);
        } catch (IOException ex) {
            Logger.getLogger(TiffImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public void close() {
        reader.dispose();
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
            .addProperty(LWS.mediaType, "image/tiff")
            .addLiteral(LWS.sizeInBytes, sizeInBytes);
        TIFFImageReader rr = (TIFFImageReader) reader;        
        TIFFImageMetadata td;
        try {
            td = (TIFFImageMetadata) rr.getImageMetadata(0);
            if (td.getTIFFField(TIFF.TAG_XMP)!=null) {
                String xml = new String((byte[]) td.getTIFFField(TIFF.TAG_XMP).getValue());
                m.add(XMP.getXMP(root.getURI(), xml));
            }
            if(td.getTIFFField(TIFF.TAG_IMAGE_DESCRIPTION)!=null) {
                if (td.getTIFFField(TIFF.TAG_IMAGE_DESCRIPTION).getValue() instanceof String desc) {
                    if (!desc.trim().isEmpty()) {
                        root.addLiteral(EXIF.imageDescription, desc.trim());
                    }
                }
            }
            if (td.getTIFFField(TIFF.TAG_X_RESOLUTION)!=null) {
                if (td.getTIFFField(TIFF.TAG_X_RESOLUTION).getValue() instanceof Rational r) {
                    root.addLiteral(EXIF.xResolution, r.longValue());
                }
            }
            if (td.getTIFFField(TIFF.TAG_Y_RESOLUTION)!=null) {
                if (td.getTIFFField(TIFF.TAG_Y_RESOLUTION).getValue() instanceof Rational r) {
                    root.addLiteral(EXIF.yResolution, r.longValue());
                }
            }
            if (td.getTIFFField(TIFF.TAG_RESOLUTION_UNIT)!=null) {
                root.addLiteral(EXIF.resolutionUnit, Short.valueOf(td.getTIFFField(TIFF.TAG_RESOLUTION_UNIT).getValueAsString()));
            }
        } catch (IOException ex) {
            Logger.getLogger(TiffImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }        
        return m;        
    }

    @Override
    public Model getMeta() {                
        return getMeta(uri);
    }

    @Override
    public Set<String> getSupportedFormats() {
        Set<String> set = new HashSet<>();
        set.add("tif");
        set.add("tiff");
        return set;
    }

    @Override
    public Model readTileMeta(ImageRegion region, com.ebremer.halcyon.lib.Rectangle preferredsize) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    public static void main(String[] args) throws IOException {
        File file = new File("D:\\HalcyonStorage\\tcga\\brca\\tif\\TCGA-E2-A1B1-01Z-00-DX1.7C8DF153-B09B-44C7-87B8-14591E319354.tif");
        TiffImageReader reader = new TiffImageReader(file.toURI(), file.toURI());
        RDFDataMgr.write(System.out, reader.getMeta(), Lang.TURTLE);
    }
}
