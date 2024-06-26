package com.ebremer.halcyon.utils;

import com.ebremer.halcyon.lib.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
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
    
    public static byte[] BufferedImage2JPG(BufferedImage bi, float scale) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
        jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpegParams.setCompressionQuality(scale);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageOutputStream imageOut = ImageIO.createImageOutputStream(baos);
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
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageOutputStream imageOut=ImageIO.createImageOutputStream(baos);
            writer.setOutput(imageOut);
            writer.write(null,new IIOImage(bi,null,null),pjpegParams);
            baos.flush();
            return baos.toByteArray();
        } catch (IOException ex) {
            Logger.getLogger(ImageTools.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public static BufferedImage ScaleBufferedImage(BufferedImage bi, Rectangle preferredsize, boolean aspectratio) {
        AffineTransform at = new AffineTransform();        
        double sx = (double)preferredsize.width()/(double)bi.getWidth();
        double sy = (double)preferredsize.height()/(double)bi.getHeight();
        int px = preferredsize.width();
        int py = preferredsize.height();
        if (aspectratio) {
            if (sx<sy) {
                sy=sx;
                py=(int) (((double) bi.getHeight())*sy);
            } else {
                sx=sy;
                px=(int) (((double) bi.getWidth())*sx);
            }
        }
        at.scale(sx,sy);
        AffineTransformOp scaleOp =  new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
        BufferedImage target = new BufferedImage(px,py,(bi.getType()==0)?TYPE_3BYTE_BGR:bi.getType());
        target.createGraphics().drawImage(bi, scaleOp, (px - (int) Math.round(bi.getWidth() * sx)) / 2, (py - (int) Math.round(bi.getHeight() * sy)) / 2);
        return target;
    }

    public static BufferedImage scale(BufferedImage bi, int w, int h, boolean b) {
        double s = 1.0d/Math.max((double)bi.getWidth()/(double)w,(double)bi.getHeight()/(double)h);
        AffineTransform at = new AffineTransform();
        at.scale(s,s);
        AffineTransformOp scaleOp =  new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
        BufferedImage target = new BufferedImage(w,h,bi.getType());
        target.createGraphics().drawImage(bi, scaleOp, (w - (int) Math.round(bi.getWidth() * s)) / 2, (h - (int) Math.round(bi.getHeight() * s)) / 2);
        return target;
    }
}
