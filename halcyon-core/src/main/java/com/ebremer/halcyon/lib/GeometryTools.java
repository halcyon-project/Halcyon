package com.ebremer.halcyon.lib;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class GeometryTools {
    private static final Logger logger = LoggerFactory.getLogger(GeometryTools.class);
    
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
    
    /**
     * Parse any WKT geometry, or null if it cannot be read (M6).
     * <p>
     * Callers that only need area/length/intersection should use this rather
     * than {@link #WKT2Polygon}: JTS implements {@code getArea()},
     * {@code getLength()} and {@code intersects()} polymorphically, so a
     * {@code MULTIPOLYGON} (a pathology region with multiple parts) is handled
     * correctly — {@code getArea()}/{@code getLength()} already sum the parts.
     */
    public static Geometry WKT2Geometry(String swkt) {
        if (swkt == null || "POLYGON EMPTY".equals(swkt)) return null;
        try {
            return new WKTReader().read(swkt);
        } catch (ParseException ex) {
            logger.debug("Parse Exception --> {}", swkt);
        } catch (IllegalArgumentException ex) {
            logger.debug("ARGH --> {}", swkt);
        }
        return null;
    }

    /**
     * Parse WKT that is expected to be a single {@code POLYGON}, else null.
     * <p>
     * M6: this used to blind-cast the parsed geometry — {@code (Polygon) geometry}
     * — while catching only ParseException/IllegalArgumentException, so a
     * perfectly legal {@code MULTIPOLYGON} threw an uncaught ClassCastException
     * that aborted the whole enclosing SPARQL query. It now fails soft to null
     * (every caller already null-checks), so a multi-part geometry degrades to
     * "not handled here" instead of taking the request down.
     */
    public static Polygon WKT2Polygon(String swkt) {
        Geometry geometry = WKT2Geometry(swkt);
        if (geometry == null) return null;
        if (geometry instanceof Polygon polygon) {
            return polygon;
        }
        logger.debug("WKT2Polygon: not a POLYGON ({}) --> {}", geometry.getGeometryType(), swkt);
        return null;
    }
    
    /**
     * Scale+simplify any polygonal geometry (M6). A {@code POLYGON} behaves
     * exactly as before; a {@code MULTIPOLYGON} has each part scaled
     * independently and is re-assembled, so a multi-part annotation survives
     * {@code hal:scale} instead of being dropped (or, before M6, throwing a
     * ClassCastException that failed the query). Parts that simplify away to
     * fewer than 4 points are discarded; null if nothing survives.
     */
    public static Geometry scaleAndSimplify(Geometry geometry, double scaleFactor) {
        if (geometry instanceof Polygon polygon) {
            return scaleAndSimplifyPolygon(polygon, scaleFactor);
        }
        if (geometry instanceof MultiPolygon multi) {
            ArrayList<Polygon> parts = new ArrayList<>();
            for (int i = 0; i < multi.getNumGeometries(); i++) {
                if (multi.getGeometryN(i) instanceof Polygon part) {
                    Polygon scaled = scaleAndSimplifyPolygon(part, scaleFactor);
                    if (scaled != null) {
                        parts.add(scaled);
                    }
                }
            }
            if (parts.isEmpty()) return null;
            if (parts.size() == 1) return parts.get(0);
            return new GeometryFactory().createMultiPolygon(parts.toArray(new Polygon[0]));
        }
        return null;
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
