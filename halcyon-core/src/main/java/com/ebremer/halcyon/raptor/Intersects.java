package com.ebremer.halcyon.raptor;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

public class Intersects extends FunctionBase {

    @Override
    public NodeValue exec(List<NodeValue> args) {
        NodeValue nwkt = args.get(0);
        NodeValue nx = args.get(1);
        NodeValue ny = args.get(2);
        NodeValue nw = args.get(3);
        NodeValue nh = args.get(4);
        if (!nwkt.isString()) {
            throw new IllegalArgumentException("Intersects expects the WKT Polygon to be a String argument");
        }
        if (!nx.isInteger()) { throw new IllegalArgumentException("Intersects expects the x coordinate to be a integer argument"); }
        if (!ny.isInteger()) { throw new IllegalArgumentException("Intersects expects the y coordinate to be a integer argument"); }
        if (!nw.isInteger()) { throw new IllegalArgumentException("Intersects expects the w coordinate to be a integer argument"); }
        if (!nh.isInteger()) { throw new IllegalArgumentException("Intersects expects the h coordinate to be a integer argument"); }
        
        GeometryFactory geometryFactory = new GeometryFactory();
        int x = nx.getInteger().intValue();
        int y = ny.getInteger().intValue();
        int w = nw.getInteger().intValue();
        int h = nh.getInteger().intValue();
        Coordinate[] coords = new Coordinate[] {
            new Coordinate((x*w)  , (y*h)),
            new Coordinate((x*w)+w, (y*h)),
            new Coordinate((x*w)+w, (y*h)+h),
            new Coordinate((x*w)  , (y*h)+h),
            new Coordinate((x*w)  , (y*h))    
        };
        Polygon tile = geometryFactory.createPolygon(coords);
        WKTReader reader = new WKTReader();
        Polygon wkt = null;
        try {
            Geometry geometry = reader.read(nwkt.getString());
            wkt = (Polygon) geometry;
            return NodeValue.makeBoolean(wkt.intersects(tile));
        } catch (ParseException ex) {
            System.out.println(tile);
            System.out.println(wkt);
            Logger.getLogger(Intersects.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            System.out.println("RUNT : "+nwkt.getString());
        }
        return NodeValue.makeBoolean(false);
    }

    @Override
    public void checkBuild(String uri, ExprList args) {
        if (args.size() != 5) {
            throw new IllegalArgumentException("Intersects expects five arguments [wkt,x,y,w,h]");
        }
    }
}
