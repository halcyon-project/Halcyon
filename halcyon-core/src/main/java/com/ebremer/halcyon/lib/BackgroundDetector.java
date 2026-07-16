package com.ebremer.halcyon.lib;

import com.ebremer.halcyon.utils.ImageTools;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

public class BackgroundDetector {

    public static Color getDominantColor(BufferedImage image) {
        Map<Color, Integer> colorCount = new HashMap<>();
        for (int i = 0; i < image.getWidth(); i++) {
            for (int j = 0; j < image.getHeight(); j++) {
                Color color = new Color(image.getRGB(i, j));
                colorCount.put(color, colorCount.getOrDefault(color, 0) + 1);
            }
        }
        Color dominantColor = null;
        int maxCount = 0;
        for (Map.Entry<Color, Integer> entry : colorCount.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominantColor = entry.getKey();
            }
        }
        return dominantColor;
    }

    public static boolean isBackgroundColor(BufferedImage image, Color dominant, Color target, int tolerance) {
        return ( Math.abs(dominant.getRed() - target.getRed()) ) <= tolerance &&
               ( Math.abs(dominant.getGreen() - target.getGreen()) ) <= tolerance &&
               ( Math.abs(dominant.getBlue() - target.getBlue()) ) <= tolerance;
    }
        
    public static boolean[][] getBackgroundMask(BufferedImage bi, int a, int b, int tolerance) {
        boolean[][] mask = new boolean[bi.getWidth()][bi.getHeight()];
        Color dominant = getDominantColor(bi);
        System.out.println("Dominant color: " + dominant.toString());
        for (int i = 0; i < bi.getWidth(); i++) {
            for (int j = 0; j < bi.getHeight(); j++) {
                if (isBackgroundColor(bi, new Color(bi.getRGB(i, j)), dominant, tolerance)) {
                    bi.setRGB(i, j, Color.BLACK.getRGB());
                } else {
                    bi.setRGB(i, j, Color.WHITE.getRGB());
                }
            }
        }
        BufferedImage bix = ImageTools.ScaleBufferedImage(bi, new Rectangle(a,b), true);
        try {
            File outputfile = new File("D:\\ATAN\\adjustedmask.png");
            ImageIO.write(bix, "png", outputfile);
        } catch (IOException e) {
        }
        for (int i = 0; i < bix.getWidth(); i++) {
            for (int j = 0; j < bix.getHeight(); j++) {
                mask[i][j] = (bix.getRGB(i, j)&0x00ffffff) == 0;
            }
        }
        BufferedImage bb = new BufferedImage(bix.getWidth(),bix.getHeight(),bix.getType());
        for (int i = 1; i < bix.getWidth()-1; i++) {
            for (int j = 1; j < bix.getHeight()-1; j++) {
                mask[i][j] = mask[i][j]&&(mask[i-1][j-1]||mask[i][j-1]||mask[i+1][j-1]||mask[i+1][j]||mask[i+1][j+1]||mask[i][j+1]||mask[i-1][j+1]||mask[i-1][j]);
                if (!mask[i][j]) {
                    mask[i][j]=(mask[i-1][j-1]&&mask[i][j-1]&&mask[i+1][j-1]&&mask[i+1][j]&&mask[i+1][j+1]&&mask[i][j+1]&&mask[i-1][j+1]&&mask[i-1][j]);
                }
                if (mask[i][j]) {
                    bb.setRGB(i, j, Color.BLACK.getRGB());
                } else {
                    bb.setRGB(i, j, Color.WHITE.getRGB());
                }
            }
        }
        try {
            File outputfile = new File("D:\\ATAN\\adjustedmask2.png");
            ImageIO.write(bb, "png", outputfile);
        } catch (IOException e) {
        }
        return mask;
    }
    
    public static BufferedImage getMask(BufferedImage image, int tolerance) {
        Color dominant = getDominantColor(image);
        BufferedImage bi = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        System.out.println("Dominant color: " + dominant.toString());
        for (int i = 0; i < image.getWidth(); i++) {
            for (int j = 0; j < image.getHeight(); j++) {
                if (isBackgroundColor(image, new Color(image.getRGB(i, j)), dominant, tolerance)) {
                    bi.setRGB(i, j, Color.WHITE.getRGB());
                } else {
                    bi.setRGB(i, j, Color.BLACK.getRGB());
                }
            }
        }
        return bi;
    }
    
    public static boolean[][] getBooleanMask(BufferedImage image, int tolerance) {
        Color dominant = getDominantColor(image);
        boolean[][] mask = new boolean[image.getWidth()][image.getHeight()];
        System.out.println("Dominant color: " + dominant.toString());
        for (int i = 0; i < image.getWidth(); i++) {
            for (int j = 0; j < image.getHeight(); j++) {
                mask[i][j] = !isBackgroundColor(image, new Color(image.getRGB(i, j)), dominant, tolerance);
            }
        }
        return mask;
    }
    
    public static void Dump(BufferedImage bi, Path path) {
        try {
            ImageIO.write(bi, "png", path.toFile());
        } catch (IOException ex) {
            Logger.getLogger(BackgroundDetector.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
