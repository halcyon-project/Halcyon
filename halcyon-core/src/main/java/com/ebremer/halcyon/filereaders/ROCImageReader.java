package com.ebremer.halcyon.filereaders;

import com.ebremer.beakgraph.ng.BG;
import com.ebremer.halcyon.FL.FL;
import com.ebremer.halcyon.FL.FLPool;
import com.ebremer.halcyon.FL.FLSegmentation;
import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.Rectangle;
import com.ebremer.halcyon.lib.URITools;
import com.ebremer.halcyon.raptor.Objects.Scale;
import com.ebremer.halcyon.utils.ImageTools;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

public class ROCImageReader extends AbstractImageReader {
    private final ImageMeta meta;
    private final URI uri;
    private final URI base;
    private static final Integer METAVERSION = 0;

    public ROCImageReader(URI uri, URI base) throws IOException, Exception {
        this.uri = uri;
        if (base==null) {
            this.base = uri;
        } else {
            this.base = base;
        }        
        FL fl = FLPool.getPool().borrowObject(base);
        ImageMeta.Builder builder = ImageMeta.Builder.getBuilder(0, fl.getWidth(), fl.getHeight())
            .filter(false)
            .setTileSizeX(fl.getTileSizeX())
            .setTileSizeY(fl.getTileSizeY());
        for (int s=0; s<fl.getScales().size(); s++) {
            Scale scale = fl.getScales().get(s);
            builder.addScale(s, scale.scale(), scale.width(), scale.height());
        }
        builder.setMeta(fl.getManifest());
        FLPool.getPool().returnObject(base,fl);
        meta = builder.build();
        System.out.println(meta);
    }
    
    @Override
    public int getMetaVersion() {
        return METAVERSION;
    }

    @Override
    public String getFormat() {
        return "zip";
    }

    @Override
    public void close() {}
    
    private BufferedImage readTile(ImageRegion region, int series) {
        try {
            FL fl = FLPool.getPool().borrowObject(base);
            BufferedImage bi = fl.readTile(region, series);
            FLPool.getPool().returnObject(base,fl);
            return bi;
        } catch (Exception ex) {
            Logger.getLogger(ROCImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    private Model readTileMeta(ImageRegion region, int series) {
        try {
            FL fl = FLPool.getPool().borrowObject(base);
            Model bi = fl.readTileMeta(region, series);
            FLPool.getPool().returnObject(base,fl);
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
        return ImageTools.ScaleBufferedImage(bi,preferredsize, true);
    }

    @Override
    public ImageMeta getImageMeta() {
        return meta;
    }

    @Override
    public Model getMeta(URI xuri) {
        Model m = ModelFactory.createDefaultModel();
        try {
            FL fl = FLPool.getPool().borrowObject(base);
            //m.add(fl.getManifest());
            m.add(fl.LoadExtendedManifest(xuri));
            FLPool.getPool().returnObject(base, fl);
        } catch (Exception ex) {
            Logger.getLogger(ROCImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }
        m.createResource(URITools.fix(base))
            .addLiteral(HAL.filemetaversion, METAVERSION)
            .addLiteral(EXIF.width, meta.getWidth())
            .addLiteral(EXIF.height, meta.getHeight())
            .addProperty(RDF.type, SchemaDO.ImageObject)
            .addProperty(RDF.type, BG.BeakGraph)
            .addProperty(RDF.type, SchemaDO.Dataset);        
        return m;
    }
    
    @Override
    public Model getMeta() {
        return getMeta(base);
    }

    @Override
    public Set<String> getSupportedFormats() {
        Set<String> set = new HashSet<>();
        set.add("zip");
        return set;
    }
    
    public static void main(String[] args) throws Exception {
        File file = new File("E:\\tcga\\cvpr-data\\zip\\coad\\TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.zip");
        FLSegmentation reader = new FLSegmentation(file.toURI());
        RDFDataMgr.write(System.out, reader.getManifest(), Lang.TURTLE);
    }
}
