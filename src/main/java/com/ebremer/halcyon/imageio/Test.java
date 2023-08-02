package com.ebremer.halcyon.imageio;

import com.twelvemonkeys.imageio.metadata.tiff.TIFF;
import com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReader;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.plugins.tiff.TIFFDirectory;
import javax.imageio.plugins.tiff.TIFFField;
import javax.imageio.stream.ImageInputStream;

/**
 *
 * @author erich
 */
public class Test {
    
    public static void main(String[] args) throws IOException {
        //File file = new File("D:\\HalcyonStorage\\nasa\\eso1242a.tif");
        File file = new File("D:\\HalcyonStorage\\tcga\\brca\\TCGA-3C-AALI-01Z-00-DX1.F6E9A5DF-D8FB-45CF-B4BD-C6B76294C291.tif");
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("No reader for: " + file);
            }
            TIFFImageReader reader = (TIFFImageReader) readers.next();
            try {
                reader.setInput(input);
                System.out.println(reader.getTileWidth(0)+"--"+reader.getTileHeight(0)+"  "+reader.getWidth(0)+" X "+reader.getHeight(0));
                System.out.println(reader.getNumImages(true));
                BufferedImage image = reader.readTile(0, 10, 10);
                ImageReadParam param = reader.getDefaultReadParam();
                int numThumbs = reader.getNumThumbnails(0);
                ImageIO.write(image, "jpeg", new File("\\ATAN\\x.jpg"));
                
                IIOMetadata metadata = reader.getImageMetadata(0);
                TIFFDirectory ifd = TIFFDirectory.createFromMetadata(metadata);
                System.out.println(ifd);
                TIFFField f = ifd.getTIFFField(TIFF.TAG_JPEG_TABLES);
                System.out.println(ifd.containsTIFFField(TIFF.TAG_JPEG_TABLES));
                
                
            } finally {
                reader.dispose();
            }
        }
    }
    
}
