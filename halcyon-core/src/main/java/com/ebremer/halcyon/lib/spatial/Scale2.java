package com.ebremer.halcyon.lib.spatial;

import com.ebremer.halcyon.lib.GeometryTools;
import com.ebremer.ns.GEO;
import java.util.List;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionBase;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTWriter;

public class Scale2 extends FunctionBase {
    
    public static final String POLYGONEMPTY = "POLYGON EMPTY";

    @Override
    public NodeValue exec(List<NodeValue> args) {
        NodeValue nwkt = args.get(0);
        NodeValue scalenode = args.get(1);
        if (!nwkt.isLiteral()) {
            throw new IllegalArgumentException("Scale expects a Literal "+nwkt.toString());
        }
        if (!nwkt.getDatatypeURI().equals(GEO.wktLiteral.getURI())) {
            throw new IllegalArgumentException("Scale expects a WKT String argument "+nwkt.toString());
        }
        if (!scalenode.isDouble()) { throw new IllegalArgumentException("Scale expects the scale to a type double argument"); }
        String ppp = nwkt.getString();
        if (POLYGONEMPTY.equals(ppp)) return NodeValue.makeString(POLYGONEMPTY);
        // M6: a MULTIPOLYGON used to throw an uncaught ClassCastException here.
        // scaleAndSimplify scales each part and re-assembles, so a multi-part
        // annotation survives scaling instead of being dropped.
        Geometry geometry = GeometryTools.WKT2Geometry(ppp);
        if (geometry == null) {
            return NodeValue.makeString(POLYGONEMPTY);
        }
        Geometry scaled = GeometryTools.scaleAndSimplify(geometry, scalenode.getDouble());
        if (scaled == null) {
            return NodeValue.makeString(POLYGONEMPTY);
        }
        WKTWriter wktWriter = new WKTWriter();
        try {
            ppp = wktWriter.write(scaled);
        } catch (NullPointerException ex) {
            System.out.println(scaled +"  "+ ex.toString());
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
