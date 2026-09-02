package com.ebremer.halcyon.lib.spatial;

import com.ebremer.halcyon.lib.GeometryTools;
import com.ebremer.ns.GEO;
import java.util.List;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase;
import org.locationtech.jts.geom.Geometry;

public class Area extends FunctionBase {
    
    public static final String POLYGONEMPTY = "POLYGON EMPTY";
    
    public Area() {
        super();
    }

    @Override
    public NodeValue exec(List<NodeValue> args) {
        NodeValue nwkt = args.get(0);
        if (!nwkt.isLiteral()) {
            throw new IllegalArgumentException("Area expects a Literal "+nwkt.toString());
        }
        if (!nwkt.getDatatypeURI().equals(GEO.wktLiteral.getURI())) {
            throw new IllegalArgumentException("Area expects a WKT String argument "+nwkt.toString());
        }
        String ppp = nwkt.getString();
        if (POLYGONEMPTY.equals(ppp)) return NodeValue.makeDouble(0d);
        // M6: read as a Geometry, not a Polygon. A MULTIPOLYGON (a region with
        // several parts — routine in pathology annotations) used to hit an
        // uncaught ClassCastException here and abort the whole query. JTS's
        // Geometry.getArea() already SUMS the component areas.
        Geometry geometry = GeometryTools.WKT2Geometry(ppp);
        if (geometry == null) {
            return NodeValue.makeDouble(0d);
        }
        return NodeValue.makeDouble(geometry.getArea());
    }

    @Override
    public void checkBuild(String uri, ExprList args) {
        if (args.size() != 1) {
            throw new IllegalArgumentException("Area expects one argument [wkt]");
        }
    }
}
