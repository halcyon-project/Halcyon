package com.ebremer.halcyon.raptor;

import com.ebremer.halcyon.hilbert.HilbertSpace;
import static com.ebremer.halcyon.raptor.scale.POLYGONEMPTY;
import com.ebremer.ns.HAL;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.update.UpdateAction;
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
public class Spatial9 {
    private final Model m;
    private final ConcurrentHashMap<Resource,Polygon> buffer;
    private final Dataset ds;
    private final String uuid = "urn:uuid"+UUID.randomUUID().toString();
    private final String annotations = "urn:uuid"+UUID.randomUUID().toString();
    private final int width;
    private final int height;
    private final int tileSizeX;
    private final int tileSizeY;
    public final ArrayList<Scale> scales = new ArrayList<>();
    public record Scale(int width, int height, int numTilesX, int numTilesY) {}

    
    public Spatial9(File file, int width, int height, int tileSizeX, int tileSizeY) throws FileNotFoundException, IOException {
        FunctionRegistry.get().put(HAL.NS+"eStarts", eStarts.class);
        FunctionRegistry.get().put(HAL.NS+"Intersects", Intersects.class);
        FunctionRegistry.get().put(HAL.NS+"scale", scale.class);
        this.width = width;
        this.height = height;
        this.tileSizeX = tileSizeX;
        this.tileSizeY = tileSizeY;
        this.m = ModelFactory.createDefaultModel();
        buffer = new ConcurrentHashMap<>();
        ds = DatasetFactory.createGeneral();
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        GZIPInputStream gis = new GZIPInputStream(bis);
        RDFDataMgr.read(m, gis, Lang.TURTLE);
        System.out.println("# of Triples --> "+m.size());
        ds.setDefaultModel(m);
        ds.addNamedModel(uuid, ModelFactory.createDefaultModel());
        int max = Math.max(width, height);
        max = ((int) Math.ceil(Math.log(max)/Math.log(2))) - ((int) Math.ceil(Math.log(tileSizeX)/Math.log(2)))+1;
        System.out.println("# of levels --> "+max);
        int w = width;
        int h = height;
        for (int i=0; i<max; i++) {
            int numTilesX = (int) Math.ceil((double)w/(double)tileSizeX);
            int numTilesY = (int) Math.ceil((double)h/(double)tileSizeY);
            scales.add(new Scale(w, h, numTilesX, numTilesY));
            w = (int) Math.round((double)w/2d);
            h = (int) Math.round((double)h/2d);
        }
        scales.forEach(s->{
            System.out.println("Scale --> "+s);
        });
    }
    
    public List<RDFNode> getNGs(Polygon swkt, int scale, int w, int h) {
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
                    list.add(ResourceFactory.createResource(HAL.NS+"grid/"+scale+"/"+a+"/"+b));
                }                
            }
        } catch (IllegalArgumentException ex) {
            if (geometry!=null) {
                System.out.println("ARGH --> "+geometry.getCoordinates().length);
            } else {
                System.out.println("ARGH --> "+swkt);
            }
            Logger.getLogger(Spatial9.class.getName()).log(Level.SEVERE, null, ex);
        }
        return list;
    }
    
    public void RemoveEmptyPolygons() {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            delete where {
                graph ?annotations {
                    ?a a oa:Annotation; oa:hasBody ?body; oa:hasSelector ?selector .
                        ?body ?bp ?bo .
                        ?selector ?p ?o; rdf:value ?polygon
                }
            }
            """
        );
        pssx.setNsPrefix("oa", OA.NS);
        pssx.setNsPrefix("rdf", RDF.uri);
        pssx.setNsPrefix("hal", HAL.NS);
        pssx.setNsPrefix("so", SchemaDO.NS);
        pssx.setLiteral("polygon", POLYGONEMPTY);
        pssx.setIri("annotations", annotations);
        UpdateAction.parseExecute(pssx.toString(), ds);
    }
    
    public void CreateNamedGraphs(int scale) {
        buffer.clear();
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?a ?wkt
            where {
                graph ?annotations { ?a a oa:Annotation; oa:hasSelector/rdf:value ?wkt }
            }
            """
        );
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("annotations", annotations);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),ds);
        ResultSet rs = qe.execSelect();
        Model xx = ds.getNamedModel(uuid);
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            Polygon hp = Tools.WKT2Polygon(qs.get("wkt").asLiteral().getString());
            if (hp!=null) {
                getNGs(hp,scale,tileSizeX,tileSizeY).forEach(ng->{
                    Resource anno = qs.get("a").asResource();
                    xx.add(anno, SchemaDO.containedIn, ng);
                    buffer.put(anno, hp);
                });
            }
        }
        System.out.println("Buffer --> "+buffer.size());
    }
    
    public void SeparateAnnotations() {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            delete {
                ?a a oa:Annotation; oa:hasBody ?body; oa:hasSelector ?selector .
                ?body ?bp ?bo .
                ?selector ?selectorP ?selectorO
            }
            insert {
                graph ?annotations {
                    ?a a oa:Annotation; oa:hasBody ?body; oa:hasSelector ?selector .
                    ?body ?bp ?bo .
                    ?selector ?selectorP ?selectorO
                }
            }
            where {
                ?a a oa:Annotation; oa:hasBody ?body; oa:hasSelector ?selector .
                ?body ?bp ?bo .
                ?selector ?selectorP ?selectorO
            }
            """
        );
        pssx.setNsPrefix("oa", OA.NS);
        pssx.setNsPrefix("rdf", RDF.uri);
        pssx.setNsPrefix("hal", HAL.NS);
        pssx.setIri("annotations", annotations);
        UpdateAction.parseExecute(pssx.toString(), ds);
    }
    
    public void BinTheAnnotations() {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            delete {
                graph ?grid { ?a so:containedIn ?ng }
            }
            insert {
                graph ?ng {
                    ?a a oa:Annotation; oa:hasBody ?body; oa:hasSelector ?selector .
                       ?body ?bp ?bo .
                       ?selector ?p ?o   
                }
            }
            where {
                graph ?grid { ?a so:containedIn ?ng }
                graph ?annotations {
                    ?a a oa:Annotation; oa:hasBody ?body; oa:hasSelector ?selector .
                       ?body ?bp ?bo .
                       ?selector ?p ?o   
                }
            }
            """
        );
        pssx.setNsPrefix("oa", OA.NS);
        pssx.setNsPrefix("rdf", RDF.uri);
        pssx.setNsPrefix("hal", HAL.NS);
        pssx.setNsPrefix("so", SchemaDO.NS);
        pssx.setIri("grid", uuid);
        pssx.setIri("annotations", annotations);
        UpdateAction.parseExecute(pssx.toString(), ds);
        System.out.println("Missing polygons...");
        RDFDataMgr.write(System.out, ds.getNamedModel(uuid), Lang.TURTLE);
        System.out.println("Annotations binned...");
        System.out.println("POLYGONS --> "+buffer.size());
    }
    
    public void ScalePolygons() {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            delete { ?selector rdf:value ?wkt }
            insert { ?selector rdf:value ?scaledPolygon }
            where {
                ?selector rdf:value ?wkt
                bind (hal:scale(?wkt,0.5) as ?scaledPolygon)
            }
            """
        );
        pssx.setNsPrefix("oa", OA.NS);
        pssx.setNsPrefix("rdf", RDF.uri);
        pssx.setNsPrefix("hal", HAL.NS);
        pssx.setNsPrefix("so", SchemaDO.NS);
        pssx.setIri("annotations", annotations);
        UpdateAction.parseExecute(pssx.toString(), ds.getNamedModel(annotations));
    }
    
    public void RemoveWorkingAnnotations() {
        ds.removeNamedModel(annotations);
    }
    
    public void RemoveHilbertPolygons() {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            delete {
                graph ?annotations { 
                    ?a a oa:Annotation; oa:hasSelector ?selector .
                        ?selector hal:hasRange ?range .
                        ?range hal:low ?low; hal:high ?high
                }
            }
            where {
                graph ?annotations { 
                    ?a a oa:Annotation; oa:hasSelector ?selector .
                        ?selector hal:hasRange ?range .
                        ?range hal:low ?low; hal:high ?high
                }
            }
            """
        );
        pssx.setNsPrefix("oa", OA.NS);
        pssx.setNsPrefix("rdf", RDF.uri);
        pssx.setNsPrefix("hal", HAL.NS);
        pssx.setNsPrefix("so", SchemaDO.NS);
        pssx.setIri("annotations", annotations);
        UpdateAction.parseExecute(pssx.toString(), ds);
    }
    
    public void Protocol() throws FileNotFoundException {
        System.out.println("Separate Annotations...");
        SeparateAnnotations();
        int ns = scales.size();
        for (int s = 0; s < ns; s++) {
            if (s==1) {
                RemoveHilbertPolygons();
            }
            System.out.println("# of triples in annotations --> "+ds.getNamedModel(annotations).size());
            System.out.println("Create Named Graphs..."+s);
            CreateNamedGraphs(s);
            System.out.println("Bin Annotations..."+s);
            BinTheAnnotations();
            if (s+1 < ns) {
                System.out.println("Scaling Polygons..."+s);
                ScalePolygons();
                System.out.println("Remove Empty Polygons..."+s);
                RemoveEmptyPolygons();
            }
        }
        System.out.println("Remove Working Annotations...");
        RemoveWorkingAnnotations();
        System.out.println(" -------------------------------------------------------------------------------");
        RDFDataMgr.write(new FileOutputStream(new File("/AAA/triplesX.nq")), ds, Lang.NQUADS);
    }
    
    public void GenerateHilbertData() {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?selector ?wkt
            where {
                ?a a oa:Annotation; oa:hasSelector ?selector .
                ?selector rdf:value ?wkt
            }
            """
        );
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("annotations", annotations);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m);
        try (ExecutorService engine = Executors.newVirtualThreadPerTaskExecutor()) {
            //ConcurrentLinkedQueue<Future<Model>> list = new ConcurrentLinkedQueue<>();
            qe.execSelect().materialise().forEachRemaining(qs->{
                Resource r = qs.getResource("selector");
                engine.submit(new PolygonProcessor(r, qs.get("wkt").asLiteral().getString()));
                //Future<Model> future = );
                //list.add(future);
            });
        }
    }
    
    public static void main(String[] args) throws FileNotFoundException, IOException, FileNotFoundException, FileNotFoundException {
        File file = new File("D:\\tcga\\nuclearsegmentation2019\\rdf\\ucec\\TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675c.ttl.gz");
        //File file = new File("D:\\AAA\\oa.ttl.gz");
        Spatial9 six = new Spatial9(file,112231,82984,512,512);
        six.Protocol();
    }
    
    class PolygonProcessor implements Callable<Model> {
        private final String wkt;
        private final Resource selector;
        
        public PolygonProcessor(Resource selector, String wkt) {
            this.selector = selector;
            this.wkt = wkt;
        }

        @Override
        public Model call() throws Exception {
            HilbertSpace hs = new HilbertSpace(width, height);
            hs.Polygon2Hilbert(Tools.WKT2Polygon(wkt)).forEach(k->{
                    Resource range = selector.getModel().createResource()
                        .addLiteral(HAL.low, k.low())
                        .addLiteral(HAL.high, k.high());
                    selector.addProperty(HAL.hasRange, range);
                });
            return null;
        }  
    }     
}
