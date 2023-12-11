package com.ebremer.halcyon.lib.spatial;

import com.ebremer.halcyon.lib.GeometryTools;
import java.util.List;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase;
import org.locationtech.jts.geom.Polygon;

public class Perimeter extends FunctionBase {
    
    public static final String POLYGONEMPTY = "POLYGON EMPTY";
    
    public Perimeter() {
        super();
    }

    @Override
    public NodeValue exec(List<NodeValue> args) {
        NodeValue nwkt = args.get(0);
        if (!nwkt.isString()) {
            throw new IllegalArgumentException("Perimeter expects a WKT String argument");
        }
        String ppp = nwkt.getString();
        if (POLYGONEMPTY.equals(ppp)) return NodeValue.makeDouble(0d);
        Polygon polygon = GeometryTools.WKT2Polygon(ppp);
        if (polygon == null) {
            return NodeValue.makeDouble(0d);
        }
        return NodeValue.makeDouble(polygon.getLength());
    }

    @Override
    public void checkBuild(String uri, ExprList args) {
        if (args.size() != 1) {
            throw new IllegalArgumentException("Perimeter expects one argument [wkt]");
        }
    }
}
