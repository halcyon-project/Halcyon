package com.ebremer.halcyon.raptor;

import com.ebremer.ns.HAL;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

/**
 *
 * @author erich
 */
public class Spatial {
    
    public static List<RDFNode> getNGs(String swkt, int w, int h) {
        WKTReader reader = new WKTReader();
        List<RDFNode> list = new LinkedList<>();
        Geometry geometry = null;
        try {
            geometry = reader.read(swkt);
            Polygon wkt = (Polygon) geometry;
            Envelope bb = wkt.getEnvelopeInternal();
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
            Logger.getLogger(Spatial.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParseException ex) {
            Logger.getLogger(Spatial.class.getName()).log(Level.SEVERE, null, ex);
        }
        return list;
    }
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        FunctionRegistry.get().put(HAL.NS+"eStarts", eStarts.class);
        FunctionRegistry.get().put(HAL.NS+"Intersects", Intersects.class);

        ConcurrentHashMap<Resource,Polygon> buffer = new ConcurrentHashMap<>();
        Model m = ModelFactory.createDefaultModel();
        File file = new File("D:\\tcga\\nuclearsegmentation2019\\rdf\\ucec\\TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675c.ttl.gz");
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        GZIPInputStream gis = new GZIPInputStream(bis);
        RDFDataMgr.read(m, gis, Lang.TURTLE);
        System.out.println(m.size());
        Dataset ds = DatasetFactory.createGeneral();
        ds.setDefaultModel(m);
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?a ?wkt
            where {
                ?a a oa:Annotation; oa:hasSelector/rdf:value ?wkt
            }
            """
        );
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("hal", HAL.NS);
        System.out.println(pss.toString());
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),ds);
        //int c = 0;
        ResultSet rs = qe.execSelect().materialise();
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            //c++;
            //System.out.println(qs);
            //getNGs(qs.get("wkt").asLiteral().getString(),256,256);
            ParameterizedSparqlString pssx = new ParameterizedSparqlString(
                """
                #delete {
                #    ?a a oa:Annotation; oa:hasBody ?body; oa:hasSelector ?selector .
                #    ?body ?bp ?bo .
                #    ?selector ?p ?o; rdf:value ?wkt
                #}
                insert {
                    graph ?ng {
                        ?a a oa:Annotation; oa:hasBody ?body; oa:hasSelector ?selector .
                        ?body ?bp ?bo .
                        ?selector ?p ?o; rdf:value ?wkt
                    }
                }
                where {
                    ?a a oa:Annotation; oa:hasBody ?body; oa:hasSelector ?selector .
                    ?body ?bp ?bo .
                    ?selector ?p ?o; rdf:value ?wkt
                    values (?ng) {?nglist}
                }
                """
            );
            pssx.setNsPrefix("oa", OA.NS);
            pssx.setNsPrefix("rdf", RDF.uri);
            pssx.setNsPrefix("hal", HAL.NS);
            pssx.setIri("a", qs.get("a").asResource().getURI());
            pssx.setLiteral("wkt", qs.getLiteral("wkt").getString());
            pssx.setValues("nglist", getNGs(qs.get("wkt").asLiteral().getString(),256,256));
            //System.out.println(pssx.toString());
            UpdateRequest request = UpdateFactory.create();
            request.add(pssx.toString());
            UpdateAction.execute(request,ds);
            //System.out.println(c);
        }
        /*
        ds.getDefaultModel().removeAll();
        ds.listModelNames().forEachRemaining(r->{
            System.out.println(r+" ---> "+ds.getNamedModel(r).size());
        });
        File filex = new File("D:\\tcga\\nuclearsegmentation2019\\cool.trig");
        try (   FileOutputStream fos = new FileOutputStream(filex);
                BufferedOutputStream bos = new BufferedOutputStream(fos))
        {
            RDFDataMgr.write(bos, ds, Lang.TRIG);
        }
        */
    }
    
}
