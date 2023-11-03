package com.ebremer.halcyon.raptor.spatial;

import com.ebremer.halcyon.lib.GeometryTools;
import org.apache.jena.sparql.expr.NodeValue;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;

/**
 *
 * @author erich
 */
public class Test {
    
    public static void main(String[] args) {
      //  String poly = "POLYGON ((2872 15451, 2870 15453, 2869 15453, 2867 15455, 2866 15455, 2862 15459, 2862 15460, 2861 15461, 2861 15462, 2860 15463, 2860 15466, 2859 15467, 2859 15469, 2861 15471, 2861 15472, 2862 15473, 2865 15473, 2867 15471, 2868 15471, 2871 15468, 2871 15467, 2872 15466, 2872 15465, 2873 15464, 2873 15463, 2875 15461, 2875 15460, 2876 15459, 2876 15458, 2877 15457, 2877 15456, 2878 15455, 2877 15455, 2876 15454, 2876 15452, 2874 15452, 2872 15451))";
        String poly = "POLYGON ((0 0, 100 0, 100 100, 0 100, 0 0))";      
        Polygon polygon = GeometryTools.WKT2Polygon(poly);
        System.out.println(polygon.getArea());
        System.out.println(polygon.getLength());
        System.out.println(polygon.getCentroid());
        Envelope envelope = polygon.getEnvelopeInternal();
        double minX = envelope.getMinX();
        double minY = envelope.getMinY();
        double maxX = envelope.getMaxX();
        double maxY = envelope.getMaxY();
        System.out.println(polygon.getEnvelopeInternal());
    }
    
}
