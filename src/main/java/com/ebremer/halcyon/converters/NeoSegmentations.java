package com.ebremer.halcyon.converters;

import com.ebremer.halcyon.ExtendedPolygon;
import static com.ebremer.halcyon.converters.GeoTools.WKT2Polygon;
import com.ebremer.halcyon.utils.ImageMeta;
import com.ebremer.halcyon.utils.ImageMeta.ImageObject;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import com.ebremer.ns.SNO;
import java.awt.Polygon;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Scanner;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

/**
 * YAY
 * @author erich
 */
public class NeoSegmentations {
    private final LinkedList<ExtendedPolygon> eps = new LinkedList<>();
    private int width = 0;
    private int height = 0;
    private long points = 0;
    private long polygons = 0;
    
    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }
  
    public void Protocol2(Model m) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            select ?height ?width where {                                        
                ?CreateAction a so:CreateAction; so:object ?image .
                ?image exif:width ?width; exif:height ?height
            }
        """);
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m);
        ResultSet results = qe.execSelect();
        if (results.hasNext()) {
            System.out.println("FOUND IMAGE DATA!");
            QuerySolution soln = results.nextSolution();
            width = soln.get("width").asLiteral().getInt();
            height = soln.get("height").asLiteral().getInt();
        } else {
            System.out.println("IMAGE DATA MISSING!!\n"+pss.toString());
        }
        pss = new ParameterizedSparqlString("""
            select ?s ?polygon ?type ?hasCertainty where {
                ?s a oa:Annotation; oa:hasBody ?body; oa:hasSelector ?selector .
                ?selector a oa:FragmentSelector; rdf:value ?polygon .
                ?body hal:assertedClass ?type; hal:hasCertainty ?hasCertainty
            }
        """);
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        qe = QueryExecutionFactory.create(pss.toString(), m);
        results = qe.execSelect();
        System.out.println("WE HAVE DATA : "+results.hasNext());
        while (results.hasNext()) {
            QuerySolution soln = results.nextSolution();
            Polygon polygon = WKT2Polygon(soln.get("polygon").asLiteral().getString());
            if (polygon==null) {
                System.out.println(soln.get("s")+" --->"+soln.get("polygon").asLiteral().getString());
            } else {
                ExtendedPolygon ep = new ExtendedPolygon();
                ep.neovalue = soln.get("hasCertainty").asLiteral().getFloat();
                ep.polygon = polygon;
                ep.id = eps.size();
                ep.classid = soln.get("type").asResource().getURI();
                eps.add(ep);
            }
        }
    }
    
    public void Protocol4(ImageObject io, InputStream is) {
        width = io.width;
        height = io.height;
        Scanner s = new Scanner(is);
        s.nextLine(); //skip headers
        while (s.hasNextLine()) {
            ExtendedPolygon ep = new ExtendedPolygon();
            ep.raw = s.nextLine();
            Scanner scanner = new Scanner(ep.raw);
            scanner.useDelimiter(",");
            scanner.nextInt();  //AreaInPixels
            scanner.nextInt(); //PhysicalSize
            //String classid = scanner.next();
            //  if (classid==3) {
            String p = scanner.next();
            p = p.substring(1, p.length()-1);
            String[] wow = p.split(":");
            int wowlen = wow.length-2;
            int[] px = new int[wowlen/2];
            int[] py = new int[wowlen/2];
            polygons++;
            for (int i=0;i<wowlen;i=i+2) {  //clip off closing point
                points++;
                String xx = wow[i];
                String yy = wow[i+1];
                double dx = Double.parseDouble(xx);
                double dy = Double.parseDouble(yy);
                int x = (int)dx;
                int y = (int)dy;
                int off = i/2;
                px[off] = x;
                py[off] = y;
            }
            Polygon polygon = new Polygon(px,py,px.length);            
            ep.neovalue = 1.0f;
            ep.polygon = polygon;
            ep.id = eps.size();
            ep.classid = SNO.NuclearMaterial.getURI();
            eps.add(ep);
        }
    }

    public LinkedList GetPolygons() {
        return eps;
    }
}