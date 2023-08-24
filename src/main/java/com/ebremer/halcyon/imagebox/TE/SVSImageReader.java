package com.ebremer.halcyon.imagebox.TE;

import com.ebremer.halcyon.server.CacheService;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.formats.FormatException;
import loci.formats.gui.BufferedImageReader;
import loci.formats.in.SVSReader;

/**
 *
 * @author erich
 */
public class SVSImageReader extends AbstractImageReader {
    private BufferedImageReader reader;
    private final ImageMeta meta;
    
    public SVSImageReader(URI uri) throws IOException {
        super(uri);
        reader = new BufferedImageReader(new SVSReader());
        File file = new File(uri);
        try {
            reader.setId(file.toString());
        } catch (FormatException ex) {
            Logger.getLogger(SVSImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }
        reader.setSeries(0);
        ImageMeta.Builder builder = ImageMeta.Builder.getBuilder(0, reader.getSizeX(), reader.getSizeY())
                .setTileSizeX(reader.getOptimalTileWidth())
                .setTileSizeY(reader.getOptimalTileHeight());
        for(int i=1; i<reader.getSeriesCount();i++) {
            reader.setSeries(i);
            builder.addScale(i, reader.getSizeX(), reader.getSizeY());
        }
        meta = builder.build();
        //meta.getRDF();
    }

    @Override
    public String getFormat() {
        return "svs";
    }

    private BufferedImage readTile(ImageRegion region, int series) {
        reader.setSeries(series);
        //System.out.println(series+"   "+region+"  "+reader.getSizeX()+" "+reader.getSizeY()+"   "+CacheService.getCache().stats());
        try {
            return reader.openImage(0, region.getX(), region.getY(), region.getWidth(), region.getHeight());
        } catch (FormatException ex) {
            Logger.getLogger(SVSImageReader.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(SVSImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public void close() {
        try {
            reader.close();
        } catch (IOException ex) {
            Logger.getLogger(SVSImageReader.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public BufferedImage readTile(ImageRegion region, Rectangle preferredsize) {
        ImageMeta.ImageScale scale = meta.getBestMatch(Math.max((double) region.getWidth()/(double) preferredsize.width(),(double) region.getHeight()/ (double) preferredsize.height()));
        return readTile(scale.Validate(region.scaleRegion(scale.scale())),scale.series());
    }
    
    @Override
    public ImageMeta getMeta() {
        return meta;
    }
}
