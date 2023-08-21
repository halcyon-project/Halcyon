package com.ebremer.halcyon.imagebox.TE;

import com.ebremer.halcyon.utils.ImageTools;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.stream.ImageInputStream;

public class TiffImageReader extends AbstractImageReader {
    private javax.imageio.ImageReader reader;
    private final ImageMeta meta;

    public TiffImageReader(URI uri) throws IOException {
        super(uri);
        System.out.println("Building TiffImageReader...");
        File file = new File(uri);
        ImageInputStream input = ImageIO.createImageInputStream(file);
        Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            throw new IllegalArgumentException("No reader for: " + file);
        }
        reader = readers.next();
        reader.setInput(input);
        ImageMeta.Builder builder = ImageMeta.Builder.getBuilder(0, reader.getWidth(0), reader.getHeight(0))
            .setTileSizeX(reader.getTileWidth(0))
            .setTileSizeY(reader.getTileHeight(0));
        for (int s=1; s<reader.getNumImages(true); s++) {
            builder.addScale(s, reader.getWidth(s), reader.getHeight(s));
        }
        meta = builder.build();
        //meta.getRDF();
        System.out.println(reader.getWidth(0)+"x"+reader.getHeight(0));
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
    public void close() throws Exception {
        reader.dispose();
    }

    @Override
    public BufferedImage readTile(ImageRegion region, com.ebremer.halcyon.imagebox.TE.Rectangle preferredsize) {
        ImageMeta.ImageScale scale = meta.getBestMatch(Math.max((double) region.getWidth()/(double) preferredsize.width(),(double) region.getHeight()/ (double) preferredsize.height()));
        return ImageTools.ScaleBufferedImage(readTile(scale.Validate(region.scaleRegion(scale.scale())),scale.series()),preferredsize);
    }

    @Override
    public ImageMeta getMeta() {
        return meta;
    }
}
