package com.ebremer.halcyon.raptor;

import org.locationtech.jts.geom.Polygon;

/**
 *
 * @author erich
 */
public class Scaler {
    
    public static void main(String[] args) {
        String poly = "POLYGON ((101178 28936, 101177 28937, 101175 28937, 101173 28939, 101173 28940, 101174 28941, 101174 28942, 101175 28942, 101177 28944, 101177 28945, 101185 28953, 101186 28953, 101188 28955, 101189 28955, 101190 28956, 101191 28956, 101194 28959, 101195 28959, 101196 28960, 101197 28960, 101199 28962, 101200 28962, 101201 28963, 101202 28963, 101203 28964, 101206 28964, 101208 28962, 101208 28958, 101207 28957, 101207 28956, 101203 28952, 101203 28950, 101202 28949, 101202 28948, 101201 28947, 101201 28946, 101198 28943, 101196 28943, 101195 28942, 101193 28942, 101191 28940, 101190 28940, 101188 28938, 101186 28938, 101185 28937, 101183 28937, 101178 28936))";
        Polygon polygon = Tools.WKT2Polygon(poly);
        System.out.println(polygon+" ---> "+polygon.getCoordinates().length);
        polygon = Tools.scaleAndSimplifyPolygon(polygon, 0.5d);
        System.out.println(polygon+" ---> "+polygon.getCoordinates().length);
        polygon = Tools.scaleAndSimplifyPolygon(polygon, 0.5d);
        System.out.println(polygon+" ---> "+polygon.getCoordinates().length);
                polygon = Tools.scaleAndSimplifyPolygon(polygon, 0.5d);
        System.out.println(polygon+" ---> "+polygon.getCoordinates().length);
                polygon = Tools.scaleAndSimplifyPolygon(polygon, 0.5d);
        System.out.println(polygon+" ---> "+polygon.getCoordinates().length);
                polygon = Tools.scaleAndSimplifyPolygon(polygon, 0.5d);
        System.out.println(polygon+" ---> "+polygon.getCoordinates().length);
    }

}
