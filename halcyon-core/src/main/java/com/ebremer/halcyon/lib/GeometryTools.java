package com.ebremer.halcyon.lib;

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
