package com.ebremer.halcyon.lib;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class ImageRegion {
    private static final Logger logger = LoggerFactory.getLogger(ImageRegion.class);
    private final int x;
    private final int y;
    private final int width;
    private final int height;
  	
    public ImageRegion(final int x, final int y, final int width, final int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    public int getX() {
        return x;
    }
    
    public Polygon getPolygon() {
        GeometryFactory geometryFactory = new GeometryFactory();
        Coordinate[] squareCoordinates = new Coordinate[] {
            new Coordinate(x,y),
            new Coordinate(x+width,y),
            new Coordinate(x+width,y+height),
            new Coordinate(x,y+height),
            new Coordinate(x,y)
        };
        LinearRing squareRing = geometryFactory.createLinearRing(squareCoordinates);
        return new Polygon(squareRing, null, geometryFactory);
    }
    
    public ImageRegion scaleRegion(double ratio) {
        int sx = (int) Math.floor(x / ratio);
        int sy = (int) Math.floor(y / ratio);
        int sw = (int) Math.floor(width / ratio);
        int sh = (int) Math.floor(height / ratio);
        return new ImageRegion(sx,sy,sw,sh);
    }

    public int getY() {
        return y;
    }
        
    public int getWidth() {
        return width;
    }
         
    public int getHeight() {
        return height;
    }
    
    @Override
    public int hashCode() {
	int prime = 31;
	int result = prime + x;
	result = prime * result + y;
        result = prime * result + width;
        result = prime * result + height;
	return result;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
	if (obj == null) {
            return false;
        }
	if (getClass() != obj.getClass()) {
            return false;
        }
	ImageRegion other = (ImageRegion) obj;
        if (x != other.x)
            return false;
        if (y != other.y)
            return false;
        if (height != other.height)
            return false;
	return width == other.width;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(x).append(",").append(y).append(",").append(width).append(",").append(height).append("]");
        return (sb.toString());
    }
}
