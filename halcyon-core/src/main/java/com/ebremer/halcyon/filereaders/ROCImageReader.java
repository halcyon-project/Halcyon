package com.ebremer.halcyon.filereaders;

import com.ebremer.halcyon.FL.FL;
import com.ebremer.halcyon.FL.FLPool;
import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.Rectangle;
import com.ebremer.halcyon.lib.URITools;
import com.ebremer.halcyon.raptor.Objects.Scale;
import com.ebremer.halcyon.utils.ImageTools;
import com.ebremer.ns.EXIF;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

public class ROCImageReader extends AbstractImageReader {
    private final ImageMeta meta;
    private final URI uri;

    public ROCImageReader(URI uri) throws IOException, Exception {
        this.uri = uri;
        FL fl = FLPool.getPool().borrowObject(uri);
        ImageMeta.Builder builder = ImageMeta.Builder.getBuilder(0, fl.getWidth(), fl.getHeight())
            .filter(false)
            .setTileSizeX(fl.getTileSizeX())
            .setTileSizeY(fl.getTileSizeY());
        for (int s=0; s<fl.getScales().size(); s++) {
            Scale scale = fl.getScales().get(s);
            builder.addScale(s, scale.scale(), scale.width(), scale.height());
        }
        builder.setMeta(fl.getManifest());
        FLPool.getPool().returnObject(uri,fl);
        meta = builder.build();
    }

    @Override
    public String getFormat() {
        return "zip";
    }

    @Override
    public void close() {}
    
    private BufferedImage readTile(ImageRegion region, int series) {
        try {
            FL fl = FLPool.getPool().borrowObject(uri);
            BufferedImage bi = fl.readTile(region, series);
            FLPool.getPool().returnObject(uri,fl);
            return bi;
        } catch (Exception ex) {
            Logger.getLogger(ROCImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    private Model readTileMeta(ImageRegion region, int series) {
        try {
            FL fl = FLPool.getPool().borrowObject(uri);
            Model bi = fl.readTileMeta(region, series);
            FLPool.getPool().returnObject(uri,fl);
            return bi;
        } catch (Exception ex) {
            Logger.getLogger(ROCImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    @Override
    public Model readTileMeta(ImageRegion region, Rectangle preferredsize) {
        ImageMeta.ImageScale scale = meta.getBestMatch(Math.max((double) region.getWidth()/(double) preferredsize.width(),(double) region.getHeight()/ (double) preferredsize.height()));
        //return ImageTools.ScaleBufferedImage(readTile(scale.Validate(region.scaleRegion(scale.scale())),scale.series()),preferredsize);
        Model m = readTileMeta(scale.Validate(region.scaleRegion(scale.scale())),scale.series());
        return m;
    }
    
    @Override
    public BufferedImage readTile(ImageRegion region, com.ebremer.halcyon.lib.Rectangle preferredsize) {
        ImageMeta.ImageScale scale = meta.getBestMatch(Math.max((double) region.getWidth()/(double) preferredsize.width(),(double) region.getHeight()/ (double) preferredsize.height()));
        ImageRegion ir = region.scaleRegion(scale.scale());
        ir = scale.Validate(ir);
        BufferedImage bi = readTile(ir,scale.series());
        return ImageTools.ScaleBufferedImage(bi,preferredsize);
    }

    @Override
    public ImageMeta getImageMeta() {
        return meta;
    }

    @Override
    public Model getMeta() {
        Model m = ModelFactory.createDefaultModel();
        try {
            FL fl = FLPool.getPool().borrowObject(uri);
            //m.add(fl.getManifest());
            m.add(fl.LoadExtendedManifest());
            FLPool.getPool().returnObject(uri, fl);
        } catch (Exception ex) {
            Logger.getLogger(ROCImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }
        m.createResource(URITools.fix(uri))
            .addLiteral(EXIF.width, meta.getWidth())
            .addLiteral(EXIF.height, meta.getHeight())
            .addProperty(RDF.type, SchemaDO.ImageObject)
            .addProperty(RDF.type, SchemaDO.Dataset);        
        return m;
    }

    @Override
    public Set<String> getSupportedFormats() {
        Set<String> set = new HashSet<>();
        set.add("zip");
        return set;
    }
}
