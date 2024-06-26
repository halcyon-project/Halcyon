package com.ebremer.halcyon.lib;

import com.ebremer.halcyon.utils.HalJsonLD;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
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
    private byte[] jpg = null;
    private byte[] png = null;
    private Model meta = null;
    
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
    
    public byte[] getJPG() {
        getBufferedImage();
        if (jpg==null) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
            jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            jpegParams.setCompressionQuality(0.7f);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageOutputStream imageOut = ImageIO.createImageOutputStream(baos);
                writer.setOutput(imageOut);
                writer.write(null,new IIOImage(bi,null,null),jpegParams);                
                baos.flush();
                jpg = baos.toByteArray();
            } catch (IOException ex) {
                Logger.getLogger(Tile.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return jpg;
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
    
    public byte[] getPNG() {
        getBufferedImage();
        if (png==null) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
            ImageWriteParam pjpegParams = writer.getDefaultWriteParam();
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageOutputStream imageOut=ImageIO.createImageOutputStream(baos);
                writer.setOutput(imageOut);
                writer.write(null,new IIOImage(bi,null,null),pjpegParams);
                baos.flush();
                png = baos.toByteArray();
            } catch (IOException ex) {
                Logger.getLogger(Tile.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return png;
    }
}
