package com.ebremer.halcyon.imagebox.TE;

import java.util.ArrayList;
import java.util.Collections;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 *
 * @author erich
 */
public class ImageMeta {
    private final ArrayList<ImageScale> scales;
    private final int series;
    private final int width;
    private final int height;
    private final int tileSizeX;
    private final int tileSizeY;
    private final float aspectratio;
    private final Model meta;
    
    private ImageMeta(Builder builder) {
        this.series = builder.series;
        this.width = builder.width;
        this.height = builder.height;
        this.tileSizeX = builder.tileSizeX;
        this.tileSizeY = builder.tileSizeY;
        this.scales = builder.scales;
        this.aspectratio = builder.aspectratio;
        this.meta = ModelFactory.createDefaultModel();
    }
    
    public ArrayList<ImageScale> getScales() {
        return scales;
    }
    
    public Model getRDF() {
        return meta;
    }
    
    public ImageScale getBestMatch(double ratio) {
        int c = scales.size();
        ImageScale scale;
        do {
            c--;
            scale = scales.get(c);
        } while (ratio<scale.scale);
        return scale;
    }
    
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
    
    public int getTileSizeX() {
        return tileSizeX;
    }

    public int getTileSizeY() {
        return tileSizeY;
    }
    
    public int getMainImageSeries() {
        return series;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(width).append("x").append(height).append(" ").append(tileSizeX).append("x").append(tileSizeY).append("\n");
        for (ImageScale s : scales) {
            sb.append(s.scale).append("->").append(s.width).append("x").append(s.height).append("\n");
        }
        return sb.toString();
    }

    public record ImageScale(int series, int scale, int width, int height, double aspectratio) implements Comparable<ImageScale> {
        @Override
        public int compareTo(ImageScale other) {
            return Float.compare(this.scale, other.scale);
        }
        
        public ImageRegion Validate(ImageRegion region) {
           if (((region.getX()+region.getWidth()) < width)&&((region.getY()+region.getHeight()) < height)) {
                return region;
            }
            int w = region.getX()+region.getWidth();
            int h = region.getY()+region.getHeight();
            w = (w >= width) ? width - region.getX(): region.getWidth();
            h = (h >= height) ? height - region.getY(): region.getHeight();
            return new ImageRegion(region.getX(),region.getY(),w,h);
        }
    };
    
    public static class Builder {
        private final ArrayList<ImageScale> scales;
        private int series;
        private int width;
        private int height;
        private int tileSizeX;
        private int tileSizeY;
        private final boolean useWidth;
        private final float aspectratio;

        private Builder(int series, int width, int height) {
            this.series = series;
            this.width = width;
            this.height = height;
            this.aspectratio = ((float)width)/((float)height);
            useWidth = (width>=height);
            scales = new ArrayList<>();
            scales.add(new ImageScale(0,1,width,height,((float)width)/((float)height)));
        }
        
        public Builder setSeries(int series) {
            this.series = series;
            return this;
        }
        
        public Builder setTileSizeX(int tileSizeX) {
            this.tileSizeX = tileSizeX;
            return this;
        }

        public Builder setTileSizeY(int tileSizeY) {
            this.tileSizeY = tileSizeY;
            return this;
        }
        
        public Builder addScale(int series, int width, int height) {
            int scale = Math.round((useWidth) ? ((float) this.width)/((float) width) : ((float) this.height)/((float) height));
            float ratio = ((float)width)/((float)height);
            float d = Math.abs(ratio-aspectratio);
            d = d/aspectratio;
            if (d<0.00153) {
                //System.out.println("Adding Scale "+series+"  "+d+"  "+ratio+"  "+scales.size()+" "+width+" x "+height);
                scales.add(new ImageScale(series,scale,width,height,ratio));
            } else {
                //System.out.println("Aspect Ratio different "+series+"  "+d+"  "+ratio+"  "+scales.size()+" "+width+" x "+height);
            }
            return this;
        }
        
        public static Builder getBuilder(int series, int width, int height) {
            return new Builder(series, width, height);
        }
        
        public ImageMeta build() {
            Collections.sort(scales);
            return new ImageMeta(this);
        }
    }
    
    public static void main(String[] args) {
        ImageMeta im = ImageMeta.Builder.getBuilder(0,1024,512)
                .setSeries(0)
                .setTileSizeX(240)
                .setTileSizeY(241)
                .addScale(4, 64, 32)
                .addScale(5, 32, 16)
                .addScale(2, 256, 128)
                .addScale(1, 512, 256)
                .addScale(3, 128, 64)
                .build();
        System.out.println(im);
    }
}

