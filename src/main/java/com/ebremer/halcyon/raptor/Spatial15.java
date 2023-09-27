package com.ebremer.halcyon.raptor;

import static com.ebremer.halcyon.raptor.scale.POLYGONEMPTY;
import com.ebremer.ns.HAL;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

/**
 *
 * @author erich
 */
public class Spatial15 {
    
    public static List<RDFNode> getNGs(Polygon swkt, int w, int h) {
        List<RDFNode> list = new LinkedList<>();
        Geometry geometry = null;
        try {
            Envelope bb = swkt.getEnvelopeInternal();
            int minx = (int) (bb.getMinX()/w);
            int miny = (int) (bb.getMinY()/h);
            int maxx = (int) Math.ceil(bb.getMaxX()/w);
            int maxy = (int) Math.ceil(bb.getMaxY()/h);
            for (int a=minx; a<maxx;a++) {
                for (int b=miny; b<maxy;b++) {
                    list.add(ResourceFactory.createResource(HAL.NS+"grid/0/"+a+"/"+b));
                }                
            }
        } catch (IllegalArgumentException ex) {
            if (geometry!=null) {
                System.out.println("ARGH --> "+geometry.getCoordinates().length);
            } else {
                System.out.println("ARGH --> "+swkt);
            }
            Logger.getLogger(Spatial15.class.getName()).log(Level.SEVERE, null, ex);
        }
        return list;
    }
    
    public static void main(String[] args) throws FileNotFoundException, IOException, FileNotFoundException, FileNotFoundException {
        FunctionRegistry.get().put(HAL.NS+"eStarts", eStarts.class);
        FunctionRegistry.get().put(HAL.NS+"Intersects", Intersects.class);
        FunctionRegistry.get().put(HAL.NS+"scale", scale.class);
        Model m = ModelFactory.createDefaultModel();
        ConcurrentHashMap<Resource,Polygon> buffer = new ConcurrentHashMap<>();
        File file = new File("D:\\tcga\\nuclearsegmentation2019\\rdf\\ucec\\TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675c.ttl.gz");
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        GZIPInputStream gis = new GZIPInputStream(bis);
        RDFDataMgr.read(m, gis, Lang.TURTLE);
        System.out.println(m.size());
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?a ?selector ?wkt
            where {
                ?a a oa:Annotation; oa:hasSelector ?selector .
                ?selector rdf:value ?wkt
            }
            """
        );
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("hal", HAL.NS);
        System.out.println(pss.toString());
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),m);
        ResultSet rs = qe.execSelect();
        Dataset ds = DatasetFactory.createGeneral();
        ds.setDefaultModel(m);
        Model xx = ModelFactory.createDefaultModel();
        String uuid = "urn:uuid"+UUID.randomUUID().toString();
        ds.addNamedModel(uuid, xx);
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            Polygon hp = Tools.WKT2Polygon(qs.get("wkt").asLiteral().getString());
            if (hp!=null) {
                getNGs(hp,256,256).forEach(ng->{
                    Resource anno = qs.get("a").asResource();
                    xx.add(anno, SchemaDO.containedIn, ng);
                    buffer.put(anno, hp);
                });
            }
        }
        System.out.println("Name Graphs Generated...");
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            delete {
                ?a a oa:Annotation; oa:hasBody ?body; oa:hasSelector ?selector .
                ?body ?bp ?bo .
                ?selector ?p ?o
            }
            insert {
                graph ?a {
                    ?a a oa:Annotation; oa:hasBody ?body; oa:hasSelector ?selector .
                    ?body ?bp ?bo .
                    ?selector ?p ?o
                }
            }
            where {
                ?a a oa:Annotation; oa:hasBody ?body; oa:hasSelector ?selector .
                ?body ?bp ?bo .
                ?selector ?p ?o
            }
            """
        );
        pssx.setNsPrefix("oa", OA.NS);
        pssx.setNsPrefix("rdf", RDF.uri);
        pssx.setNsPrefix("hal", HAL.NS);
        UpdateRequest request = UpdateFactory.create();
        request.add(pssx.toString());
        UpdateAction.execute(request,ds);
        RDFDataMgr.write(System.out, ds.getDefaultModel(), Lang.TURTLE);
        System.out.println("Annotations separated...");
        pssx = new ParameterizedSparqlString(
            """
            delete {
                graph ?grid { ?a so:containedIn ?ng }
                graph ?a { ?s ?p ?o }
            }
            insert {
                graph ?ng { ?s ?p ?o }
            }
            where {
                graph ?grid { ?a so:containedIn ?ng }
                graph ?a { ?s ?p ?o }
            }
            """
        );
        pssx.setNsPrefix("oa", OA.NS);
        pssx.setNsPrefix("rdf", RDF.uri);
        pssx.setNsPrefix("hal", HAL.NS);
        pssx.setNsPrefix("so", SchemaDO.NS);
        pssx.setIri("grid", uuid);
        request = UpdateFactory.create();
        request.add(pssx.toString());
        UpdateAction.execute(request,ds);
        System.out.println("Missing polygons...");
        RDFDataMgr.write(System.out, ds.getNamedModel(uuid), Lang.TURTLE);
        System.out.println("Annotations binned...");
        System.out.println("POLYGONS --> "+buffer.size());
        //ds.listNames().forEachRemaining(cc->{
          //  System.out.println(cc+" -------------------------------------------------------------------------------");
        //RDFDataMgr.write(new FileOutputStream(new File("/AAA/triples2.nq")), ds, Lang.NQUADS);
  //      });
        System.out.println("Analysis....");
        long start = System.nanoTime();
        pssx = new ParameterizedSparqlString(
            """
            delete {
                graph ?grid { ?bnode rdf:value ?wkt}
            }
            insert {
                graph ?grid { ?bnode rdf:value ?scaledPolygon }
            }
            where {
                graph ?grid { ?bnode a oa:FragmentSelector ; rdf:value ?wkt }
                bind (hal:scale(?wkt,0.5) as ?scaledPolygon)
            }
            """
        );
        pssx.setNsPrefix("oa", OA.NS);
        pssx.setNsPrefix("rdf", RDF.uri);
        pssx.setNsPrefix("hal", HAL.NS);
        pssx.setNsPrefix("so", SchemaDO.NS);
        request = UpdateFactory.create();
        UpdateAction.execute(request,ds);
        RemoveEmptyPolygons(ds);
        System.out.println("Analysis....scale 1");
        request.add(pssx.toString());
        UpdateAction.execute(request,ds);
        RemoveEmptyPolygons(ds);
        System.out.println("Analysis....scale 2");
        request.add(pssx.toString());
        UpdateAction.execute(request,ds);
        RemoveEmptyPolygons(ds);
        System.out.println("Analysis....scale 3");
        request.add(pssx.toString());
        UpdateAction.execute(request,ds);
        RemoveEmptyPolygons(ds);
        System.out.println("Analysis....scale 4");
        UpdateAction.execute(request,ds);
        RemoveEmptyPolygons(ds);
        System.out.println("Analysis....scale 5");
        UpdateAction.execute(request,ds);
        RemoveEmptyPolygons(ds);
        System.out.println("Analysis....scale 6");
        UpdateAction.execute(request,ds);
        RemoveEmptyPolygons(ds);
        System.out.println("Analysis....scale 7");
        RDFDataMgr.write(new FileOutputStream(new File("/AAA/scaledtriples2.nq")), ds, Lang.NQUADS);
        System.out.println(((double) System.nanoTime()-start)/1000000000d);
        System.out.println("DONE! "+rs.hasNext());
    }
    
    public static void main2(String[] args) throws FileNotFoundException {
        Dataset ds = DatasetFactory.create();
        RDFDataMgr.read(ds, new FileInputStream(new File("/AAA/scaledtriples2.nq")), Lang.NQUADS);
        RemoveEmptyPolygons(ds);
        RDFDataMgr.write(new FileOutputStream(new File("/AAA/scaledtriples3.nq")), ds, Lang.NQUADS);
    }
    
    public static void RemoveEmptyPolygons(Dataset ds) {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            delete where {
                graph ?g {
                    ?a a oa:Annotation; oa:hasBody ?body; oa:hasSelector ?selector .
                        ?body ?bp ?bo .
                        ?selector ?p ?o; rdf:value ?polygon}
            }
            """
        );
        pssx.setNsPrefix("oa", OA.NS);
        pssx.setNsPrefix("rdf", RDF.uri);
        pssx.setNsPrefix("hal", HAL.NS);
        pssx.setNsPrefix("so", SchemaDO.NS);
        pssx.setLiteral("polygon", POLYGONEMPTY);
        UpdateRequest request = UpdateFactory.create(pssx.toString());
        UpdateAction.execute(request,ds);
    }   
}
