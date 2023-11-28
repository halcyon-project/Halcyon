package com.ebremer.halcyon.converters;

import com.ebremer.halcyon.converters.hold.Legacy;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.ebremer.halcyon.lib.ExtendedPolygon;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.utils.ImageMeta;
import com.ebremer.halcyon.utils.ImageMeta.ImageObject;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.GEO;
import com.ebremer.ns.HAL;
import com.ebremer.ns.PROVO;
import com.ebremer.ns.SNO;
import java.awt.Polygon;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.RIOT;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class HeatMap2GEOSPARQL {
    private final ThreadPoolExecutor engine;
    public static final String NAME = "heatmap2geosparql";
    
    public HeatMap2GEOSPARQL(int cores) {
        engine = new ThreadPoolExecutor(cores,cores,0L,TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        engine.prestartAllCoreThreads();
    }
    
    public void shutdown() {
        System.out.println("All jobs submitted.");
        int totaljobs = engine.getQueue().size()+engine.getActiveCount();
        engine.shutdown();
        while (!engine.isTerminated()) {
            int c = engine.getQueue().size()+engine.getActiveCount();
            long ram = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1024L/1024L;
            System.out.println("jobs completed : "+(totaljobs-c)+" remaining jobs: "+c+"  Total RAM used : "+ram+"MB  Maximum RAM : "+(Runtime.getRuntime().maxMemory()/1024L/1024L)+"MB");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(HeatMap2GEOSPARQL.class.getName()).log(Level.SEVERE, null, ex);
            }
        }  
        System.out.println("Engine shutdown");
    }

    public void Tran(ImageObject io, Path src, String name, Path destination, Resource classuri) {
        engine.submit(new HeatMap2GEOSPARQL.FileProcessor(io, src, name, destination, classuri));
    }
    
    public static void main(String[] args) throws IOException {
        //ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
//        root.setLevel(ch.qos.logback.classic.Level.OFF);
        HeatMap2GEOParameters params = new HeatMap2GEOParameters();   
        JCommander jc = JCommander.newBuilder().addObject(params).build();
        jc.setProgramName(NAME);    
        try {
            jc.parse(args);
            if (params.isHelp()) {
                jc.usage();
                System.exit(0);
            } else {
                if (params.src.exists()) {
                    ImageMeta im = new ImageMeta();
                    HeatMap2GEOSPARQL heatmap2gs = new HeatMap2GEOSPARQL(params.threads);
                    Path targetbase = Path.of(params.src.getAbsoluteFile().toPath().getParent().toString(),"rdf",params.type);
                    System.out.println("TARGET BASE : "+targetbase);
                    File ddd = targetbase.toFile();
                    if (!ddd.exists()) {
                        ddd.mkdirs();
                    }
                    Files.list(params.src.toPath())
                        .filter(p->{
                            return !p.toFile().getName().startsWith("meta_");
                        })
                        .forEach(p->{
                            String name = p.getFileName().toString();
                            name = name.substring(0, name.length()-".json.gz".length());
                            name = name.substring(8)+".svs";
                            name = name.toUpperCase();
                            if (im.meta.containsKey(name)) {
                                System.out.println("Processing       : "+name);
                                ImageObject io = im.meta.get(name);
                                Path destination = Path.of(targetbase.toString(),name.substring(0, name.length()-4)+".ttl");
                                heatmap2gs.Tran(io, p, name, destination, SNO.Lymphocytes);                    
                            } else {
                                System.out.println("Missing METADATA : "+name);
                            }
                    });
                    heatmap2gs.shutdown();             
                } else {
                    System.out.println("Source does not exist! "+params.src);
                }
            }
        } catch (ParameterException ex) {
            if (params.version) {
                System.out.println(NAME+" - Version : "+HalcyonSettings.VERSION);
            } else {
                System.out.println(ex.getMessage());
            }
        }
    }
    
    class FileProcessor implements Runnable {
        private final ImageObject io;
        private final Path src;
        private final Path destination;
        private final Resource classuri;
        
        public FileProcessor(ImageObject io, Path src, String name, Path destination, Resource classuri) {
            this.io = io;
            this.src = src;
            this.destination = destination;
            this.classuri = classuri;
        }
          
    public void DumpModel(Model m, Path file, boolean compress) {
        if (!file.getParent().toFile().exists()) file.getParent().toFile().mkdirs();
        try {
            OutputStream fos;
            if (compress) {
                fos = new GZIPOutputStream(new FileOutputStream(file.toFile()+".gz"));
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
    
    public String Polygon2WKT(Polygon p) {
        StringBuilder sb = new StringBuilder();
        sb.append("POLYGON ((");
        for(int x=0; x<p.npoints; x++) {
            sb
                .append(p.xpoints[x])
                .append(" ")
                .append(p.ypoints[x])
                .append(", ");
        }
        sb
            .append(p.xpoints[0])
            .append(" ")
            .append(p.ypoints[0])
            .append("))");
        return sb.toString();
    }
    
    public void AddPolygons(Resource FC) {
        Model m = FC.getModel();
        HeatMap h = new HeatMap(io.width,io.height);
        try {
            LinkedList<ExtendedPolygon> eps = h.ProcessHeatMap(src.toFile());
            eps.forEach(p->{                    
                Resource geometry = m.createResource()
                        .addLiteral(GEO.asWKT, Polygon2WKT(p.polygon));
                Resource measurement =  m.createResource()
                        .addLiteral(HAL.hasProbability, (Float) p.neovalue)
                        .addProperty(HAL.classification, classuri);                
                Resource feature = m.createResource()
                    .addProperty(RDF.type, GEO.Feature)
                    .addProperty(HAL.classification, classuri)
                    .addProperty(HAL.measurement, measurement)
                    .addProperty(GEO.hasGeometry, geometry);
                FC.addProperty(RDFS.member, feature);               
            });
        } catch (IOException ex) {
            Logger.getLogger(HeatMap2GEOSPARQL.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run() {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("so", SchemaDO.NS);
        m.setNsPrefix("rdfs", RDFS.uri);
        m.setNsPrefix("exif", EXIF.NS);
        m.setNsPrefix("xsd", XSD.NS);
        m.setNsPrefix("sno", SNO.NS);
        FileInputStream fis;
        try {
            fis = new FileInputStream(src.toFile().getParentFile().getParent()+File.separatorChar+"meta.ttl");
            System.out.println(src);
            RDFParser.create()
                .source(fis)
                .base("")
                .lang(Lang.TTL)
                .parse(m);
            ResIterator ri = m.listResourcesWithProperty(RDF.type, GEO.FeatureCollection);
            Resource fc;
            if (ri.hasNext()) {
                fc = ri.next();
            } else {
                throw new Error("Can't find FeatureCollection");
            }
            Literal width = m.createTypedLiteral(String.valueOf(io.width), XSD.xint.getURI());
            Literal height = m.createTypedLiteral(String.valueOf(io.height), XSD.xint.getURI());
            Resource kk = m.createResource("urn:md5:"+io.md5)
                    .addProperty(RDF.type, SchemaDO.ImageObject)
                    .addLiteral(EXIF.width, width)
                    .addLiteral(EXIF.height, height);
            Resource activity = fc.getRequiredProperty(PROVO.wasGeneratedBy).getObject().asResource();
            activity.addProperty(PROVO.used, kk);
            AddPolygons(fc);
            DumpModel(m,destination,true);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(HeatMap2GEOSPARQL.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    }
}
