package com.ebremer.halcyon.lib;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.util.Iterator;
import java.util.LinkedList;

/**
 *
 * @author erich
 */
public final class ExtendedPolygons {
    private final LinkedList<ExtendedPolygon> bulk;
    private final Polygon[] polygons;
    private final Area[] areas;
    private final Object[] neovalues;
    
    public ExtendedPolygons(LinkedList<ExtendedPolygon> p) {
        bulk = p;
        polygons = new Polygon[bulk.size()];
        areas = new Area[bulk.size()];
        neovalues = new Object[bulk.size()];
        Iterator<ExtendedPolygon> ii = bulk.iterator();
        int i = 0;
        while (ii.hasNext()) {
            ExtendedPolygon ep = ii.next();
            polygons[i] = ep.polygon;
            areas[i] = new Area(ep.polygon);
            neovalues[i] = ep.neovalue;
            i++;
        }       
    }

    public LinkedList<ExtendedPolygon> GetPolygons() {
        return bulk;
    }    
    
    public LinkedList<ExtendedPolygon> GetPolygons(int x, int y, int width, int height) {
        LinkedList<ExtendedPolygon> poly = new LinkedList<>();        
        Rectangle r = new Rectangle(x,y,x+width-1,y+height-1);
        for (int i=0;i<polygons.length;i++) {
            if (areas[i].intersects(r)) {
                ExtendedPolygon ep = new ExtendedPolygon();
                ep.polygon = polygons[i];
                ep.neovalue = neovalues[i];
                poly.add(ep);
            }
        }
        return poly;
    }   
}