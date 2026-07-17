package com.ebremer.halcyon.utils;

import java.awt.Graphics2D;
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
        // L9: identity short-circuit. Every tile is scaled TWICE — once inside
        // TiffImageReader.readTile (which hardcodes aspectratio=true) and again in
        // TileRequest immediately after — and with no early exit the second pass
        // paid for a full-size allocation plus a bilinear resample to produce a
        // pixel-for-pixel copy. Guarded on getType() too, because type 0
        // (TYPE_CUSTOM) is the one case where this method is also doing a format
        // conversion to TYPE_3BYTE_BGR and must not be skipped.
        if (sx == 1.0d && sy == 1.0d && bi.getWidth() == px && bi.getHeight() == py && bi.getType() != 0) {
            return bi;
        }
        at.scale(sx,sy);
        AffineTransformOp scaleOp =  new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
        BufferedImage target = new BufferedImage(px,py,(bi.getType()==0)?TYPE_3BYTE_BGR:bi.getType());
        // dispose(): createGraphics() was called and dropped on the floor, twice per
        // tile, holding native resources until the GC got round to the finalizer.
        Graphics2D g = target.createGraphics();
        try {
            g.drawImage(bi, scaleOp, (px - (int) Math.round(bi.getWidth() * sx)) / 2, (py - (int) Math.round(bi.getHeight() * sy)) / 2);
        } finally {
            g.dispose();
        }
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
