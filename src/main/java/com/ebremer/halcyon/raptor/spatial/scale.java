package com.ebremer.halcyon.raptor.spatial;

import com.ebremer.halcyon.raptor.Tools;
import java.util.List;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTWriter;

public class scale extends FunctionBase {
    
    public static final String POLYGONEMPTY = "POLYGON EMPTY";

    @Override
    public NodeValue exec(List<NodeValue> args) {
        NodeValue nwkt = args.get(0);
        NodeValue scalenode = args.get(1);
        if (!nwkt.isString()) {
            throw new IllegalArgumentException("Intersects expects the WKT Polygon to be a String argument");
        }
        if (!scalenode.isDouble()) { throw new IllegalArgumentException("Scale expects the scale to a type double argument"); }
        String ppp = nwkt.getString();
        if (POLYGONEMPTY.equals(ppp)) return NodeValue.makeString(POLYGONEMPTY);
        Polygon polygon = Tools.WKT2Polygon(ppp);
        if (polygon == null) {
            return NodeValue.makeString(POLYGONEMPTY);
        }
        polygon = Tools.scaleAndSimplifyPolygon(polygon,scalenode.getDouble());
        if (polygon == null) {
            return NodeValue.makeString(POLYGONEMPTY);
        }
        WKTWriter wktWriter = new WKTWriter();
        try {
        ppp = wktWriter.write(polygon);
        } catch (NullPointerException ex) {
            System.out.println(polygon +"  "+ ex.toString());
        }
        return NodeValue.makeString(ppp);
    }

    @Override
    public void checkBuild(String uri, ExprList args) {
        if (args.size() != 2) {
            throw new IllegalArgumentException("Scale expects two arguments [wkt,scale]");
        }
    }
}
