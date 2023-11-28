package com.ebremer.halcyon.filereaders;

import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.URITools;
import com.ebremer.halcyon.utils.ImageTools;
import com.ebremer.ns.EXIF;
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
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

public class TiffImageReader extends AbstractImageReader {
    private javax.imageio.ImageReader reader;
    private final ImageMeta meta;
    private final URI uri;

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
        m.createResource(URITools.fix(uri))
            .addLiteral(EXIF.width, meta.getWidth())
            .addLiteral(EXIF.height, meta.getHeight())
            .addProperty(RDF.type, SchemaDO.ImageObject);
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
}
