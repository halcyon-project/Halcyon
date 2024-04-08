package com.ebremer.halcyon.filereaders;

import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.URITools;
import com.ebremer.halcyon.lib.XMP;
import com.ebremer.halcyon.utils.ImageTools;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
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

public class TiffImageReader extends AbstractImageReader {
    private javax.imageio.ImageReader reader;
    private final ImageMeta meta;
    private final URI uri;
    private static final int METAVERSION = 0;

    public TiffImageReader(URI uri) throws IOException {
        this.uri = uri;
        File file = new File(uri);
        ImageInputStream input = ImageIO.createImageInputStream(file);
        Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName("tif");
        javax.imageio.ImageReader ir = null;
        while (readers.hasNext()) {
            ir = readers.next();
            System.out.println("IMAGE CLASS --> "+ir.getClass().getCanonicalName());
            if ("com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReader".equals(ir.getClass().getCanonicalName())) {
                System.out.println("YES! : "+ir.getClass().getCanonicalName());
                reader = ir;
            } else {
                System.out.println("NOPE : "+ir.getClass().getCanonicalName());
            }
        }
        if (ir==null) {
            throw new IllegalArgumentException("No reader for: " + file);
        }
        System.out.println("READER IS ---> "+reader.getClass().getCanonicalName());
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
        return ImageTools.ScaleBufferedImage(readTile(scale.Validate(region.scaleRegion(scale.scale())),scale.series()),preferredsize);
    }

    @Override
    public ImageMeta getImageMeta() {
        return meta;
    }

    @Override
    public Model getMeta() {                
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("exif", EXIF.NS);
        m.setNsPrefix("sdo", SchemaDO.NS);
        m.setNsPrefix("hal", HAL.NS);
        m.setNsPrefix("xsd", XSD.getURI());
        Resource root = m.createResource(URITools.fix(uri))                
            .addLiteral(HAL.filemetaversion, (Integer) METAVERSION)
            .addLiteral(EXIF.width, meta.getWidth())
            .addLiteral(EXIF.height, meta.getHeight())
            .addProperty(RDF.type, SchemaDO.ImageObject);        
        TIFFImageReader rr = (TIFFImageReader) reader;        
        TIFFImageMetadata td;
        try {
            td = (TIFFImageMetadata) rr.getImageMetadata(0);
            Object xr = td.getTIFFField(TIFF.TAG_X_RESOLUTION).getValue();
            Object yr = td.getTIFFField(TIFF.TAG_Y_RESOLUTION).getValue();
            Object id = td.getTIFFField(TIFF.TAG_IMAGE_DESCRIPTION).getValue();
            Object xmp = td.getTIFFField(TIFF.TAG_XMP).getValue();
            String xml = new String((byte[]) xmp);
            m.add(XMP.getXMP(root.getURI(), xml));
            if (id instanceof String desc) {
                if (!desc.trim().isEmpty()) {
                    root.addLiteral(EXIF.imageDescription, desc.trim());
                }
            }
            if (xr instanceof Rational r) {               
                root.addLiteral(EXIF.xResolution, r.longValue());
            }
            if (yr instanceof Rational r) {
                root.addLiteral(EXIF.yResolution, r.longValue());
            }
            root.addLiteral(EXIF.resolutionUnit, Short.valueOf(td.getTIFFField(TIFF.TAG_RESOLUTION_UNIT).getValueAsString()));
        } catch (IOException ex) {
            Logger.getLogger(TiffImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }        
        return m;
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
        TiffImageReader reader = new TiffImageReader(file.toURI());
        RDFDataMgr.write(System.out, reader.getMeta(), Lang.TURTLE);
    }
}
