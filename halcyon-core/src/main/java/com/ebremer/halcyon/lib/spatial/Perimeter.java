package com.ebremer.halcyon.lib.spatial;

import com.ebremer.halcyon.lib.GeometryTools;
import com.ebremer.ns.GEO;
import java.util.List;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.expr.nodevalue.NodeValueNode;
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
        if (!nwkt.isLiteral()) {
            throw new IllegalArgumentException("Area expects a Literal "+nwkt.toString());
        }
        if (!nwkt.getDatatypeURI().equals(GEO.wktLiteral.getURI())) {
            throw new IllegalArgumentException("Area expects a WKT String argument "+nwkt.toString());
        }
        String ppp = null;
        if (nwkt instanceof NodeValueNode nnn) {
            ppp = nnn.asString();
        }
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
