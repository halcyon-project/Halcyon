package com.ebremer.halcyon.converters;

import com.ebremer.beakgraph.rdf.BeakWriter;
import com.ebremer.halcyon.Standard;
import com.ebremer.halcyon.hilbert.HilbertSpace;
import static com.ebremer.halcyon.hilbert.HilbertSpace.Area;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import java.awt.Polygon;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParserBuilder;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.RIOT;
import org.apache.jena.shared.Lock;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SchemaDO;
import org.davidmoten.hilbert.Range;

/**
 *
 * @author erich
 */
public class Engine {
    private final Model m;
    private int width;
    private int height;
    public record polygon (int polygon , long low, long high) {};
    private final boolean optimize;
    private int scale = 1;
    
    public Engine(Model m, boolean optimize) {
        this.m = m;
        this.optimize = optimize;
        System.out.println("# of triples : "+m.size());
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            #select ?object ?height ?width where {
            select * where {
                ?CreateAction a so:CreateAction;
                so:object ?object .
                ?object exif:width ?width; exif:height ?height
            } limit 1
            """); 
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("rdfs", RDFS.uri);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("exif", "http://www.w3.org/2003/12/exif/ns#");
        pss.setNsPrefix("hal", "https://www.ebremer.com/halcyon/ns/");
        pss.setNsPrefix("dcmi", "http://purl.org/dc/terms/");
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m);
        ResultSet rs = qe.execSelect();
        if (rs.hasNext()) {
            QuerySolution qs = rs.next();
            width = qs.get("width").asLiteral().getInt();
            height = qs.get("height").asLiteral().getInt();
        } else {
            throw new Error("Cannot find CreateAction");
        }
        System.out.println(width+", "+height);
    }
    
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
    
    public void Strip() {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            delete
            where {
                ?body a hal:ProbabilityBody
            }
            """); 
        pss.setNsPrefix("", "https://www.ebremer.com/ns/");
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("rdfs", RDFS.uri);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("exif", "http://www.w3.org/2003/12/exif/ns#");
        pss.setNsPrefix("hal", "https://www.ebremer.com/halcyon/ns/");
        pss.setNsPrefix("dcmi", "http://purl.org/dc/terms/");
        UpdateRequest update = UpdateFactory.create();
        update.add(pss.toString());
        pss = new ParameterizedSparqlString(
            """
            delete
            where {
                ?selector <http://purl.org/dc/terms/conformsTo> <http://www.w3.org/TR/SVG/>;
                oa:hasSource ?md5
            }
            """); 
        pss.setNsPrefix("", "https://www.ebremer.com/ns/");
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("rdfs", RDFS.uri);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("exif", "http://www.w3.org/2003/12/exif/ns#");
        pss.setNsPrefix("hal", "https://www.ebremer.com/halcyon/ns/");
        pss.setNsPrefix("dcmi", "http://purl.org/dc/terms/");
        update.add(pss.toString());
        pss = new ParameterizedSparqlString(
            """
            delete
            where {
                ?annotation a oa:Annotation
            }
            """); 
        pss.setNsPrefix("", "https://www.ebremer.com/ns/");
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("rdfs", RDFS.uri);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("exif", "http://www.w3.org/2003/12/exif/ns#");
        pss.setNsPrefix("hal", "https://www.ebremer.com/halcyon/ns/");
        pss.setNsPrefix("dcmi", "http://purl.org/dc/terms/");
        update.add(pss.toString());
        UpdateAction.execute(update, m);
        System.out.println("Strip  # of triples : "+m.size());
    }
    
    public void FindScale() {
        System.out.println("Find Scale..."+width+", "+height);
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?selector ?polygon where {
                ?selector a oa:FragmentSelector; rdf:value ?polygon
            } limit 1
            """); 
        pss.setNsPrefix("", "https://www.ebremer.com/ns/");
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("rdfs", RDFS.uri);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("exif", "http://www.w3.org/2003/12/exif/ns#");
        pss.setNsPrefix("hal", "https://www.ebremer.com/halcyon/ns/");
        pss.setNsPrefix("dcmi", "http://purl.org/dc/terms/");
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m);
        ResultSet rs = qe.execSelect();
        if (!rs.hasNext()) {
            throw new Error("Can't find any polygons!");
        }
        QuerySolution qs = rs.next();
        String polygon = qs.get("polygon").asLiteral().getString();
        Polygon p = GeoTools.WKT2Polygon(polygon);
        if (p.npoints!=5) {
            throw new Error("Only Squares allowed in Heatmap! "+polygon);
        }
        System.out.println("POLYGON : "+polygon);
        for (int u=0; u<p.npoints; u++) {
            System.out.println("Point : "+p.xpoints[u]+", "+p.ypoints[u]);
        }
        scale = (int) Math.ceil(Math.sqrt(Math.pow(p.xpoints[1]-p.xpoints[0],2)+Math.pow(p.ypoints[1]-p.ypoints[0],2)));
        System.out.println("Scale : "+scale);
        width = Math.round(width/scale);
        height = Math.round(height/scale);
    }
    
    public void HilbertPhase(BeakWriter bw) {
        if (optimize) {
            FindScale();
        }
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?selector ?polygon where {
                ?selector a oa:FragmentSelector; rdf:value ?polygon
            }
            """); 
        pss.setNsPrefix("", HAL.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("rdfs", RDFS.uri);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("dcmi", DCTerms.NS);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m);
        try (ExecutorService engine = Executors.newVirtualThreadPerTaskExecutor()) {
            ConcurrentLinkedQueue<Future<Model>> list = new ConcurrentLinkedQueue<>();
            qe.execSelect().materialise().forEachRemaining(qs->{
                Resource r = qs.getResource("selector");
                String polygon = qs.get("polygon").asLiteral().getString();
                Callable<Model> worker = new PolygonProcessor(r,polygon);
                engine.submit(worker);
                Future<Model> future = engine.submit(worker);
                list.add(future);
            });
            Phase2();
            engine.shutdown();
            do {
                for (Future<Model> f : list) {
                    if (f.isDone()) {
                        try {
                            bw.Add(f.get());
                            list.remove(f);
                        } catch (InterruptedException ex) {
                            System.out.println(ex.getMessage());
                            Logger.getLogger(Engine.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (ExecutionException ex) {
                            System.out.println(ex.getMessage());
                            Logger.getLogger(Engine.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Ingest.class.getName()).log(Level.SEVERE, null, ex);
                }
            } while (!list.isEmpty());
        }
    }

    public void Phase0() {
        m.enterCriticalSection(Lock.WRITE);
        UpdateRequest update = UpdateFactory.create();
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            delete {?selector dcterms:conformsTo ?ct }
            where {?selector dcterms:conformsTo ?ct }
            """); 
        pss.setNsPrefix("", "https://www.ebremer.com/ns/");
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("rdfs", RDFS.uri);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("exif", "http://www.w3.org/2003/12/exif/ns#");
        pss.setNsPrefix("hal", "https://www.ebremer.com/halcyon/ns/");
        pss.setNsPrefix("dcterms", "http://purl.org/dc/terms/");
        update.add(pss.toString());
        pss = new ParameterizedSparqlString(
            """
            delete {?selector oa:hasSource ?src }
            where {?selector oa:hasSource ?src }
            """); 
        pss.setNsPrefix("", "https://www.ebremer.com/ns/");
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("rdfs", RDFS.uri);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("exif", "http://www.w3.org/2003/12/exif/ns#");
        pss.setNsPrefix("hal", "https://www.ebremer.com/halcyon/ns/");
        pss.setNsPrefix("dcterms", "http://purl.org/dc/terms/");
        update.add(pss.toString());
        UpdateAction.execute(update, m);
        m.leaveCriticalSection();
        System.out.println("Phase 0 # of triples : "+m.size());
    }
    
    public void Phase2() {
        m.enterCriticalSection(Lock.WRITE);
        UpdateRequest update = UpdateFactory.create();
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            delete {?selector a oa:FragmentSelector; rdf:value ?polygon }
            where {?selector a oa:FragmentSelector; rdf:value ?polygon }
            """); 
        pss.setNsPrefix("", "https://www.ebremer.com/ns/");
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("rdfs", RDFS.uri);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("exif", "http://www.w3.org/2003/12/exif/ns#");
        pss.setNsPrefix("hal", "https://www.ebremer.com/halcyon/ns/");
        pss.setNsPrefix("dcterms", "http://purl.org/dc/terms/");
        update.add(pss.toString());
        UpdateAction.execute(update, m);
        m.leaveCriticalSection();
    }
    
    public static Model Load(File f, Resource rde) throws FileNotFoundException, IOException {
        RDFParserBuilder parser = RDFParserBuilder.create();
        Model x = parser
            .base(rde.getURI())
            .set(RIOT.symTurtleOmitBase, true)
            .lang(Lang.TURTLE)
            .source(new GZIPInputStream(new FileInputStream(f)))
            .toModel();      
        return x;
    }
    
    class PolygonProcessor implements Callable<Model> {
        private final Resource r;
        private final String cpolygon;

        public PolygonProcessor(Resource r, String cpolygon) {
            this.r = r;
            this.cpolygon = cpolygon;
        }

        @Override
        public Model call() {
            Model pm = ModelFactory.createDefaultModel();
            Polygon polygon = GeoTools.WKT2Polygon(cpolygon);
            //System.out.println("ANALYZE : "+cpolygon);
            if (polygon==null) {
                System.out.println("NULL POLYGON!!!!!");
                return pm;
            }
            if (optimize) {
                if (polygon.npoints!=5) {
                    throw new Error("Only Squares allowed in Heatmap! "+cpolygon);
                }
                //System.out.println("POLYGON ----> "+polygon.xpoints[0]/scale+", "+polygon.ypoints[0]/scale);
                HilbertSpace hs = new HilbertSpace(width, height);
                long hp = hs.hc.index(Math.round(polygon.xpoints[0]/scale), Math.round(polygon.ypoints[0]/scale));
                Property hasRange1 = pm.createProperty(HAL.hasRange.toString()+"/1");
                Property low1 = pm.createProperty(HAL.low.toString()+"/1");
                Property high1 = pm.createProperty(HAL.high.toString()+"/1");
                Range ra = new Range(hp,hp);
                    Resource range = pm.createResource()
                        .addLiteral(low1, ra.low())
                        .addLiteral(high1, ra.high());
                pm.add(r, hasRange1, range);
            } else {
                HilbertSpace hs = new HilbertSpace(width, height);
                int numscales = 1;
                if (hs.bits > 8) {
                    numscales = hs.bits-8;
                }
                LinkedList<Range> xa = hs.Polygon2Hilbert(polygon);
                Property hasRange0 = pm.createProperty(HAL.hasRange.toString()+"/0");
                Property low0 = pm.createProperty(HAL.low.toString()+"/0");
                Property high0 = pm.createProperty(HAL.high.toString()+"/0");
                xa.forEach(k->{
                    Resource range = pm.createResource()
                        .addLiteral(low0, k.low())
                        .addLiteral(high0, k.high());
                    pm.add(r, hasRange0, range);
                });
                boolean done = false;
                int i = 1;
                while ((!done)&&(i<numscales)) {
                    LinkedList<Range> neo = new LinkedList<>();
                    xa.forEach(rr->{
                        neo.add(new Range(rr.low()/4,rr.high()/4));
                    });
                    xa = HilbertSpace.compact(neo);
                    if (Area(xa)>4) {
                        Property hasRangei = pm.createProperty(HAL.hasRange.toString()+"/"+i);
                        Property lown = pm.createProperty(HAL.low.toString()+"/"+i);
                        Property highn = pm.createProperty(HAL.high.toString()+"/"+i);
                        xa.forEach(k->{
                            Resource range = pm.createResource()
                                .addLiteral(lown, k.low())
                                .addLiteral(highn, k.high());
                            pm.add(r, hasRangei, range);
                        });
                    } else {
                        done = true;
                    }
                    i++;
                }
            }
            return pm;
        }
    }
    
    public void DumpModel(Model m, Path file, boolean compress) {
        if (!file.getParent().toFile().exists()) file.getParent().toFile().mkdirs();
        try {
            OutputStream fos;
            if (compress) {
                fos = new GZIPOutputStream(new FileOutputStream(file.toFile()));
            } else {
                fos = new FileOutputStream(file.toFile());
            }
            Context context = new Context();
            context.setTrue(RIOT.symTurtleOmitBase);
            RDFWriter.create()
                .source(m)
                .context(context)
                .lang(Lang.TURTLE)
                .base("")
                .output(fos); 
            fos.flush();
            fos.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Legacy.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Legacy.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static Model getMeta(Model m, Resource r) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            construct where {
                ?roc
                    a so:Dataset;
                    hal:hasCreateAction ?ca;
                    ?rocp ?roco .
                ?ca
                    a so:CreateAction;
                    ?cap ?cao
            }
            """, Standard.getStandardPrefixes());
        pss.setIri("newroc", r.getURI());
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),m);
        Model k = qe.execConstruct();
        UpdateRequest update = UpdateFactory.create();
        pss = new ParameterizedSparqlString(
        """
        delete {
            ?roc ?p ?o .
            ?s ?pp ?roc
        }
        insert {
            ?newroc ?p ?o .
            ?s ?pp ?newroc .
        }
        where {
            ?roc ?p ?o .
            ?s ?pp ?roc .
            {
                select distinct ?roc where {
                    ?roc
                        a hal:HalcyonGraph;
                        ?p ?o .
                    ?ca
                        a so:CreateAction;
                        so:result ?roc
                }
            }
        }
        """, Standard.getStandardPrefixes());
        pss.setIri("newroc", r.getURI());
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        update.add(pss.toString());
        pss = new ParameterizedSparqlString("delete where {?roc a hal:HalcyonROCrate}", Standard.getStandardPrefixes());
        pss.setIri("newroc", r.getURI());
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        update.add(pss.toString());
        UpdateAction.execute(update, k);
        return k;
    }
    
    public static Model getHalcyonFeatures(Model m, Resource r) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?class
            where {
                ?body hal:assertedClass ?class
            }
            """, Standard.getStandardPrefixes());
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m);
        ResultSet rs = qe.execSelect();
        Features features = new Features();
        rs.forEachRemaining(qs->{
            Resource g = qs.get("class").asResource();
            System.out.println("Asserted class --> "+g.toString());
            features.AddClass(g.toString());
        });
        return features.getModel(r);
    }
}
