package com.ebremer.halcyon.raptor.spatial;

import com.ebremer.halcyon.raptor.Tools;
import java.util.List;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase;
import org.locationtech.jts.geom.Polygon;

public class Area extends FunctionBase {
    
    public static final String POLYGONEMPTY = "POLYGON EMPTY";
    
    public Area() {
        super();
    }

    @Override
    public NodeValue exec(List<NodeValue> args) {
        NodeValue nwkt = args.get(0);
        if (!nwkt.isString()) {
            throw new IllegalArgumentException("Area expects a WKT String argument");
        }
        String ppp = nwkt.getString();
        if (POLYGONEMPTY.equals(ppp)) return NodeValue.makeDouble(0d);
        Polygon polygon = Tools.WKT2Polygon(ppp);
        if (polygon == null) {
            return NodeValue.makeDouble(0d);
        }
        return NodeValue.makeDouble(polygon.getArea());
    }

    @Override
    public void checkBuild(String uri, ExprList args) {
        if (args.size() != 1) {
            throw new IllegalArgumentException("Area expects one argument [wkt]");
        }
    }
}
