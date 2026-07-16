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
public class Tile {
    public static enum TileType {RDF, BUFFEREDIMAGE};   
    private final TileRequest tilerequest;
    private BufferedImage bi = null;
    private Model meta = null;
    
    public TileRequest getTileRequest() {
        return this.tilerequest;
    }
    
    public Tile(TileRequest tilerequest, BufferedImage bi) {
        this.tilerequest = tilerequest;
        this.bi = bi;
    }

    public Tile(TileRequest tilerequest) {
        this.tilerequest = tilerequest;
    }    

    public void setBufferedImage(BufferedImage bi) {
        this.bi = bi;
    }
    
    public void setMeta(Model m) {
        meta = m;
    }
    
    public BufferedImage getBufferedImage() {
        if (bi==null) {
            bi = tilerequest.getBufferedImage(tilerequest.MaintainAspectRatio());
        }
        return bi;
    }
    
    public String getMeta(RDFFormat format) {
        if (meta==null) {
            meta = tilerequest.getMeta();
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            RDFDataMgr.write(bos, meta, format);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }
    
    public void getMeta(RDFFormat format, OutputStream out) {
        if (meta==null) {
            meta = tilerequest.getMeta();
        }
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
