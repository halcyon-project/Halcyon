package com.ebremer.halcyon.lib.imaging;

import com.ebremer.halcyon.lib.GeometryTools;
import com.ebremer.ns.GEO;
import com.ebremer.ns.HAL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;

/**
 *
 * @author erich
 */
public class Test {
    public static int TileSizeX = 200;
    public static int TileSizeY = 200;
    public static int width = 112231;
    public static int height = 82984;
    public static int nx = width/TileSizeX;
    public static int ny = height/TileSizeY;
    
    public record Pair(int x, int y) {};
    public record Anno(Pair pair, String classification, Polygon polygon) {};

    public static HashMap<Pair,Set<Anno>> annos = new HashMap<>();
    
    public static void main(String args[]) throws Exception {
        System.out.println(nx+" "+ny);
        
        Model m = ModelFactory.createDefaultModel();
        RDFDataMgr.read(m, "E:\\tcga\\cvpr-data\\rdf\\coad\\TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.ttl.gz", Lang.TURTLE);
        System.out.println(m.size());
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?classification ?wkt
            where {
            ?feature a geo:Feature;
                geo:hasGeometry [ geo:asWKT ?wkt ];
                hal:classification ?classification
            }
            """);
        pss.setNsPrefix("geo", GEO.NS);
        pss.setNsPrefix("hal", HAL.NS);
        ResultSet rs = QueryExecutionFactory.create(pss.toString(),m).execSelect();
        rs.forEachRemaining(qs->{
            Polygon poly = GeometryTools.WKT2Polygon(qs.get("wkt").asLiteral().getString());
            String classification = qs.get("classification").toString();
            if (poly!=null) {
            Envelope e = poly.getEnvelopeInternal();
            Pair p1 = new Pair((int) e.getMinX()/TileSizeX, (int) e.getMinY()/TileSizeY);
            Pair p2 = new Pair((int) e.getMaxX()/TileSizeX, (int) e.getMinY()/TileSizeY);
            Pair p3 = new Pair((int) e.getMaxX()/TileSizeX, (int) e.getMaxY()/TileSizeY);
            Pair p4 = new Pair((int) e.getMinX()/TileSizeX, (int) e.getMaxY()/TileSizeY);
            Anno a1 = new Anno(p1, classification, poly);
            Anno a2 = new Anno(p2, classification, poly);
            Anno a3 = new Anno(p3, classification, poly);
            Anno a4 = new Anno(p4, classification, poly);

            Set<Anno> set = annos.getOrDefault(p1, (new HashSet<>()));
            set.add(a1);
            annos.put(p1, set);
            set = annos.getOrDefault(p2, (new HashSet<>()));
            set.add(a2);
            annos.put(p2, set);
            set = annos.getOrDefault(p3, (new HashSet<>()));
            set.add(a3);
            annos.put(p3, set);
            set = annos.getOrDefault(p4, (new HashSet<>()));
            set.add(a4);
            annos.put(p4, set);
            }
        });

        annos.forEach((k,v)->{
            System.out.println(k+" "+v.size());
        });
        //ResultSetFormatter.out(System.out, rs);
        
        /*
        TileEngine te = TileEngine.Builder.newInstance("D:\\HalcyonStorage\\tcga\\brca\\tif\\TCGA-E2-A1B1-01Z-00-DX1.7C8DF153-B09B-44C7-87B8-14591E319354.tif")
            .setTileSizeX(200)
            .setTileSizeY(200)
            .build();

        Polygon[] roi = List.of(getPolygon(50000,0,2000,2000), getPolygon(11520,43200,2000,2000)).toArray(new Polygon[0]);

        te.stream(roi)
        //te.stream(roi, Options.create().OnlyTissue())
            .parallel()
            .filter(Filters.DONOTHING())
            .forEach(tile->tile.Write(Path.of("/dump")));
        */
    }
}
