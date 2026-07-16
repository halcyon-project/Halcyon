package com.ebremer.halcyon.lib;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

/**
 *
 * @author erich
 */
public class GeometryTools {
    
    public static BufferedImage copyBufferedImage(BufferedImage original) {
        // Create a new BufferedImage with the same dimensions and type as the original
        BufferedImage copy = new BufferedImage(original.getWidth(), original.getHeight(), original.getType());

        // Get the Graphics2D object from the copy
        Graphics2D g2d = copy.createGraphics();

        // Draw the original image onto the copy
        g2d.drawImage(original, 0, 0, null);

        // Dispose the Graphics2D object to release resources
        g2d.dispose();

        return copy;
    }

    public static Polygon lumpPolygon(Polygon polygon, int tileSizeX, int tileSizeY) {
        Coordinate[] originalCoords = polygon.getCoordinates();
        Coordinate[] translatedCoords = new Coordinate[originalCoords.length];
        for (int i = 0; i < originalCoords.length; i++) {
            translatedCoords[i] = new Coordinate(
                ((int) originalCoords[i].x /tileSizeX),
            ((int) originalCoords[i].y/tileSizeY)
            );
        }
        GeometryFactory geometryFactory = new GeometryFactory();
        LinearRing shell = geometryFactory.createLinearRing(translatedCoords);
        return geometryFactory.createPolygon(shell, null);
    }  
    
    public static Polygon getPolygon(int x, int y, int w, int h) {
        GeometryFactory geometryFactory = new GeometryFactory();
        Coordinate[] squareCoordinates = new Coordinate[] {
            new Coordinate(x,y),
            new Coordinate(x+w,y),
            new Coordinate(x+w,y+h),
            new Coordinate(x,y+h),
            new Coordinate(x,y)
        };
        LinearRing squareRing = geometryFactory.createLinearRing(squareCoordinates);
        return new Polygon(squareRing, null, geometryFactory);
    }
    
    public static Polygon translatePolygon(Polygon polygon, double translateX, double translateY) {
        Coordinate[] originalCoords = polygon.getCoordinates();
        Coordinate[] translatedCoords = new Coordinate[originalCoords.length];
        for (int i = 0; i < originalCoords.length; i++) {
            translatedCoords[i] = new Coordinate(
                originalCoords[i].x - translateX,
                originalCoords[i].y - translateY
            );
        }
        GeometryFactory geometryFactory = new GeometryFactory();
        LinearRing shell = geometryFactory.createLinearRing(translatedCoords);
        return geometryFactory.createPolygon(shell, null);
    }  
    
   public static java.awt.Polygon convertJTSToAWTPolygon(Polygon jtsPolygon) {
        Coordinate[] coordinates = jtsPolygon.getCoordinates();
        int[] xpoints = new int[coordinates.length];
        int[] ypoints = new int[coordinates.length];
        for (int i = 0; i < coordinates.length; i++) {
            xpoints[i] = (int) coordinates[i].x;
            ypoints[i] = (int) coordinates[i].y;
        }
        return new java.awt.Polygon(xpoints, ypoints, coordinates.length);
    } 
    
    public static Polygon WKT2Polygon(String swkt) {
        if ("POLYGON EMPTY".equals(swkt)) return null;
        WKTReader reader = new WKTReader();
        Geometry geometry = null;
        Polygon polygon = null;
        try {
            geometry = reader.read(swkt);
            polygon = (Polygon) geometry;
        } catch (ParseException ex) {
            System.out.println("Parse Exception --> "+swkt);
        } catch (IllegalArgumentException ex) {
            if (geometry!=null) {
                System.out.println("ARGH --> "+geometry.getCoordinates().length);
            } else {
                System.out.println("ARGH --> "+swkt);
            }
        }
        return polygon;
    }
    
    public static Polygon scaleAndSimplifyPolygon(Polygon polygon, double scaleFactor) {        
        AffineTransformation transformation = new AffineTransformation();
        transformation.scale(scaleFactor, scaleFactor);
        Geometry scaledPolygon = transformation.transform(polygon);
        HashSet<Coordinate> uniqueCoords = new HashSet<>(scaledPolygon.getCoordinates().length);
        ArrayList<Coordinate> roundedCoordsList = new ArrayList<>();
        for (Coordinate coord : scaledPolygon.getCoordinates()) {
            Coordinate roundedCoord = new Coordinate(Math.round(coord.x), Math.round(coord.y));
            if (uniqueCoords.add(roundedCoord)) {
                roundedCoordsList.add(roundedCoord);
            }
        }
        if (roundedCoordsList.size() >= 3 && !roundedCoordsList.get(0).equals2D(roundedCoordsList.get(roundedCoordsList.size() - 1))) {
            roundedCoordsList.add(roundedCoordsList.get(0));
        }
        if (roundedCoordsList.size() < 4) {
            return null;
        }
        Coordinate[] roundedCoordsArray = roundedCoordsList.toArray(new Coordinate[0]);
        GeometryFactory geometryFactory = new GeometryFactory();
        LinearRing roundedRing = geometryFactory.createLinearRing(roundedCoordsArray);
        return geometryFactory.createPolygon(roundedRing, null);
    }
    
    public static java.awt.Polygon JTS2AWT(Polygon jtsPolygon) {
        java.awt.Polygon awtPolygon = new java.awt.Polygon();
        Coordinate[] coordinates = jtsPolygon.getCoordinates();
        for (Coordinate coordinate : coordinates) {
            awtPolygon.addPoint((int) coordinate.x, (int) coordinate.y);
        }        
        return awtPolygon;
    }
    
    public static java.awt.Polygon JTS2AWT(Polygon jtsPolygon, int offsetx, int offsety) {
        java.awt.Polygon awtPolygon = new java.awt.Polygon();
        Coordinate[] coordinates = jtsPolygon.getCoordinates();
        for (Coordinate coordinate : coordinates) {
            awtPolygon.addPoint((int) (coordinate.x - offsetx), (int) (coordinate.y - offsety));
        }        
        return awtPolygon;
    }
}
