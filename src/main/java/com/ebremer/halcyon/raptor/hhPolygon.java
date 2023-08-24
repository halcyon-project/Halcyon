package com.ebremer.halcyon.raptor;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

/**
 *
 * @author erich
 */
public class hhPolygon {
    private Polygon polygon;
    
    public hhPolygon(String swkt) {
        WKTReader reader = new WKTReader();
        Geometry geometry = null;
        polygon = null;
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
    }
    
    public Polygon getPolygon() {
        return polygon;
    }
}
