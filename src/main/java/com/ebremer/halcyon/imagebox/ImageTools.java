package com.ebremer.halcyon.imagebox;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;

/**
 *
 * @author erich
 */
public class ImageTools {
    
    public static byte[] BufferedImage2JPG(BufferedImage bi) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
        jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpegParams.setCompressionQuality(1.0f);
        try (
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageOutputStream imageOut = ImageIO.createImageOutputStream(baos);   
        )
        {            
                writer.setOutput(imageOut);
                writer.write(null,new IIOImage(bi,null,null),jpegParams);   
                baos.flush();
                return baos.toByteArray();
        } catch (IOException ex) {
            Logger.getLogger(ImageTools.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public static byte[] BufferedImage2PNG(BufferedImage bi) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        ImageWriteParam pjpegParams = writer.getDefaultWriteParam();
        try  (
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageOutputStream imageOut=ImageIO.createImageOutputStream(baos);
        )
        {
            writer.setOutput(imageOut);
            writer.write(null,new IIOImage(bi,null,null),pjpegParams);
            baos.flush();
            return baos.toByteArray();
        } catch (IOException ex) {
            Logger.getLogger(ImageTools.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }    
}