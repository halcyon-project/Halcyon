package com.ebremer.halcyon.converters;

import com.ebremer.beakgraph.rdf.BeakWriter;
import com.ebremer.halcyon.Standard;
import com.ebremer.halcyon.hilbert.HilbertSpace;
import static com.ebremer.halcyon.hilbert.HilbertSpace.Area;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import com.ebremer.rocrate4j.ROCrate;
import com.ebremer.rocrate4j.writers.ZipWriter;
import java.awt.Polygon;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
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
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SchemaDO;
import org.davidmoten.hilbert.Range;
import org.slf4j.LoggerFactory;

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
        //System.out.println("HilbertPhase...");
        if (optimize) {
            FindScale();
        }
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?selector ?polygon where {
                ?selector a oa:FragmentSelector; rdf:value ?polygon
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
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m);
        ThreadPoolExecutor engine = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),Runtime.getRuntime().availableProcessors(),0L,TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        ConcurrentLinkedQueue<Future<Model>> list = new ConcurrentLinkedQueue<>();
        qe.execSelect().materialise().forEachRemaining(qs->{
            Resource r = qs.getResource("selector");
            String polygon = qs.get("polygon").asLiteral().getString();
            Callable<Model> worker = new PolygonProcessor(r,polygon);
            engine.submit(worker);
            Future<Model> future = engine.submit(worker);
            list.add(future);
        });
        //System.out.println("All jobs submitted.");
        Phase2();
        engine.prestartAllCoreThreads();
        //System.out.println("Engine shutdown");
        engine.shutdown();
        while (!list.isEmpty()) {
            list.forEach(f->{
                if (f.isDone()) {
                    try {
                        System.out.println("Adding : "+(engine.getQueue().size()+engine.getActiveCount()));
                        bw.Add(f.get());
                        list.remove(f);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(Engine.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (ExecutionException ex) {
                        Logger.getLogger(Engine.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
        }
        System.out.println("Phase 1 # of triples : "+m.size());
    }

    public void Phase0() {
        //System.out.println("Phase 2...");
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
        //System.out.println("Phase 2...");
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
        //System.out.println("Phase 2 # of triples : "+m.size());
    }
    
    public static Model Load(File f, Resource rde) throws FileNotFoundException, IOException {
        RDFParserBuilder parser = RDFParserBuilder.create();
        Model x = parser
            .base(rde.getURI())
            .set(RIOT.symTurtleOmitBase, true)
            .lang(Lang.TURTLE)
            .source(new GZIPInputStream(new FileInputStream(f)))
            .toModel();      
        System.out.println("Phase 0 # of triples : "+x.size());
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
                Range ra = new Range(hp,hp);
                    Property rp = pm.createProperty(HAL.hasRange.toString()+"/1");
                    Resource range = pm.createResource()
                        .addLiteral(HAL.low, ra.low())
                        .addLiteral(HAL.high, ra.high());
                pm.add(r, rp, range);
            } else {
                HilbertSpace hs = new HilbertSpace(width, height);
                int numscales = 1;
                if (hs.bits > 8) {
                    numscales = hs.bits-8;
                }
                LinkedList<Range> xa = hs.Polygon2Hilbert(polygon);
                Property hasRange0 = pm.createProperty(HAL.hasRange.toString()+"/0");
                xa.forEach(k->{
                    Resource range = pm.createResource()
                        .addLiteral(HAL.low, k.low())
                        .addLiteral(HAL.high, k.high());
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
                        xa.forEach(k->{
                            Resource range = pm.createResource()
                                .addLiteral(HAL.low, k.low())
                                .addLiteral(HAL.high, k.high());
                            pm.add(r, hasRangei, range);
                        });
                    } else {
                        done = true;
                    }
                    i++;
                }
            }
            //RDFDataMgr.write(System.out, pm, Lang.TURTLE);
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
                    a hal:HalcyonROCrate;
                    hal:hasCreateAction ?ca;
                    ?rocp ?roco .
                ?ca
                    a so:CreateAction;
                    ?cap ?cao
            }
            """, Standard.getStandardPrefixes());
        pss.setIri("newroc", r.getURI());
        //System.out.println("================== META 1 ===============================");
        //System.out.println(pss.toString());
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),m);
        Model k = qe.execConstruct();
        //System.out.println("================== META 1 dump ===============================");
        //RDFDataMgr.write(System.out, k, Lang.TURTLE);
        //System.out.println("================== META 1 dump end ===============================");
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
        update.add(pss.toString());
        pss = new ParameterizedSparqlString("delete where {?roc a hal:HalcyonROCrate}", Standard.getStandardPrefixes());
        pss.setIri("newroc", r.getURI());
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
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.OFF);

        //File raw = new File("/data2/halcyon/heatmap/TCGA-3C-AALI-01Z-00-DX1.F6E9A5DF-D8FB-45CF-B4BD-C6B76294C291.ttl.gz");
        File raw = new File("/data2/halcyon/seg/TCGA-3C-AALI-01Z-00-DX1.F6E9A5DF-D8FB-45CF-B4BD-C6B76294C291.ttl.gz");
        //File raw = new File("/data2/halcyon/j2/tumor-brca-resnet34-TCGA-3C-AALI-01Z-00-DX1.F6E9A5DF-D8FB-45CF-B4BD-C6B76294C291.ttl.gz");
        
        //Old to OA
        //ROCrate.Builder builder = new ROCrate.Builder(new ZipWriter2(new File("/HalcyonStorage/heatmaps/j3.zip")));
        ROCrate.ROCrateBuilder builder = new ROCrate.ROCrateBuilder(new ZipWriter(new File("/HalcyonStorage/segmentation/zzz.zip")));
        //ROCrate.Builder builder = new ROCrate.Builder(new ZipWriter2(new File("/data2/halcyon/x.zip")));
        //ROCrate.Builder builder = new ROCrate.Builder(new FolderWriter(new File("/data2/halcyon/x")));
        Resource rde = builder.getRDE();
        Model src = Engine.Load(raw,rde);
        Engine engine = new Engine(src, false);
        
        //System.out.println("================================= META ===============================================");
        //Model xx = Engine.getMeta(engine.m, ResourceFactory.createResource("https://bestbuy.com/ggg"));
        //RDFDataMgr.write(System.out, xx, RDFFormat.TURTLE_PRETTY);
        //System.out.println("================================= META ===============================================");        
        //Model m = engine.Create();
       // engine.DumpModel(m, good.toPath(), true);
        //Model yay = m;
        //OA to Arrow
        //System.out.println("Load Preprocessed Data...");
        //StopWatch sw = new StopWatch();
        //Model yay = Engine.Load(good);
        //sw.Lapse("# of triples : "+yay.size());
        BufferAllocator allocator = new RootAllocator();
        BeakWriter bw = new BeakWriter(allocator, builder, "halcyon");
        bw.Register(src);
        bw.CreateDictionary(allocator);
        engine.HilbertPhase(bw);
        bw.Add(src);
        bw.Create(allocator);
        System.out.println("RDE : "+rde.getURI());
        rde.getModel().add(Engine.getMeta(src, rde));
        rde.getModel().add(Engine.getHalcyonFeatures(src, rde));
        rde
            .addProperty(RDF.type, HAL.HalcyonROCrate)
            .addLiteral(EXIF.width,engine.getWidth())
            .addLiteral(EXIF.height,engine.getHeight());
         //   .addLiteral(BG.triples,yay.size());
        // metairi.getModel().add(VoID);
        rde.getModel().add(bw.getVoID(rde));
       //System.out.println("****************");
       // RDFDataMgr.write(System.out, bw.getVoID(rde), Lang.TURTLE);
       // System.out.println("****************");
        builder.build();
        
        
        //Engine engine = new Engine(yay,false);
        //engine.Search(32768, 32768, 256, 256);
        
        
        // just use it....  
        
        //String base = "http://www.ebremer.com/YAY";
        //BeakGraph g = new BeakGraph(new ROCrateReader(base, new ZipReader(new File("d:\\nlms2\\halcyon\\x.zip"))));
        //BeakGraph g = new BeakGraph(base, (new File("d:\\nlms2\\halcyon\\x.zip")).toURI());
        //Model m = ModelFactory.createModelForGraph(g);
        //Engine engine = new Engine(m,false);
        //long begin = System.nanoTime();
        //engine.Search(32768, 32768, 512, 512);
        //double end = (System.nanoTime() - begin)/1000000000d;
        //System.out.println("Search time : "+end);

    }
}
