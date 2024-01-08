package com.ebremer.halcyon.converters;

import com.ebremer.halcyon.converters.hold.NeoSegmentations;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.ebremer.halcyon.lib.ExtendedPolygon;
import com.ebremer.halcyon.lib.ExtendedPolygons;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.utils.ImageMeta;
import com.ebremer.halcyon.utils.ImageMeta.ImageObject;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.GEO;
import com.ebremer.ns.HAL;
import com.ebremer.ns.PROVO;
import com.ebremer.ns.SNO;
import java.awt.Polygon;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.RDFWriterBuilder;
import org.apache.jena.riot.RIOT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.XSD;

/**
 *
 * @author erich
 */
public class NS2GS {
    public static final String NAME = "ns2gs";
    private final ThreadPoolExecutor engine;
    
    public NS2GS(int cores) {
        engine = new ThreadPoolExecutor(cores,cores,0L,TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        engine.prestartAllCoreThreads();
    }
        
    public void Turbo(ImageMeta tcga, String base, String name, String destination) throws IOException {
        engine.submit(new FileProcessor(tcga, base, name, destination));
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
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                Logger.getLogger(NS2GS.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        System.out.println("Engine shutdown");
    }
    
    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException, IOException {
       // ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        //root.setLevel(ch.qos.logback.classic.Level.OFF);
        NS2GSParameters params = new NS2GSParameters();   
        JCommander jc = JCommander.newBuilder().addObject(params).build();
        jc.setProgramName(NAME);    
        try {
            jc.parse(args);
            if (params.isHelp()) {
                jc.usage();
                System.exit(0);
            } else {
                if (params.src.exists()) {
                    NS2GS ns2gs = new NS2GS(params.threads);
                    String destination = Path.of(params.src.getAbsoluteFile().toPath().getParent().toString(),"rdf",params.type).toString();
                    File ddd = Path.of(destination).toFile();
                    if (!ddd.exists()) {
                        ddd.mkdirs();
                    }
                    ImageMeta tcga = new ImageMeta();
                    Files.list(params.src.toPath())            
                        .forEach(p->{
                            Path t = Path.of(params.src.toString(), p.getFileName().toString(), p.getFileName().toString());
                            String name = p.getFileName().toString();
                            name = name.substring(0, name.length()-".tar.gz".length());
                            try {            
                                ns2gs.Turbo(tcga, t.toString(), name, destination);                                
                            } catch (IOException ex) {
                                Logger.getLogger(NS2GS.class.getName()).log(Level.SEVERE, null, ex);
                            }
                    });
                    ns2gs.shutdown();
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
    
    class FileProcessor implements Callable<Model> {
        private final ImageMeta tcga;
        private final String polygons;
        private final String destination;
        private final String name;
        private final NeoSegmentations ns;
        public FileProcessor(ImageMeta tcga, String polygons, String name, String destination) {
            this.tcga = tcga;
            this.polygons = polygons;
            this.destination = destination;
            this.name = name.toUpperCase();
            this.ns = new NeoSegmentations();
        }
        
        public static String Polygon2WKT(Polygon p) {
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
        
        public void MainProcess(ImageObject io) {
            ExtendedPolygons eps2 = new ExtendedPolygons(ns.GetPolygons());
            Model m = ModelFactory.createDefaultModel();
            ZonedDateTime now = ZonedDateTime.now();
            String snow = now.format(DateTimeFormatter.ISO_INSTANT);
            Literal dateTimeLiteral = m.createTypedLiteral(snow, XSDDatatype.XSDdateTime);
            LinkedList<ExtendedPolygon> list = eps2.GetPolygons();
            m.setNsPrefix("xsd", XSD.NS);
            m.setNsPrefix("sno", SNO.NS);
            m.setNsPrefix("geo", GEO.NS);
            m.setNsPrefix("dc", DCTerms.NS);
            m.setNsPrefix("rdfs", RDFS.uri);
            m.setNsPrefix("so", SchemaDO.NS);
            m.setNsPrefix("hal", HAL.NS);
            m.setNsPrefix("exif", EXIF.NS);
            m.setNsPrefix("prov", PROVO.NS);
            Literal width = m.createTypedLiteral(String.valueOf(io.width), XSDDatatype.XSDint);
            Literal height = m.createTypedLiteral(String.valueOf(io.height), XSDDatatype.XSDint);
            Resource image = m.createResource("urn:md5:"+io.md5)
                .addProperty(RDF.type, SchemaDO.ImageObject)
                .addProperty(EXIF.width,width)
                .addProperty(EXIF.height,height);
            Resource Activity = m.createResource()
                    .addProperty(RDF.type, PROVO.Activity)
                    .addProperty(PROVO.wasAssociatedWith, m.createResource("https://github.com/SBU-BMI/quip_cnn_segmentation/releases/tag/v1.1"))
                    .addProperty(PROVO.used, image);
            Resource SpatialObjectCollection = m.createResource()
                    .addProperty(RDF.type, GEO.FeatureCollection)
                    .addProperty(RDF.type, HAL.Segmentation)
                    .addProperty(DCTerms.title, "cnn-nuclear-segmentations-2019")
                    .addProperty(DCTerms.description, "Nuclear segmentation of TCGA cancer types")
                    .addProperty(DCTerms.date, dateTimeLiteral)
                    .addProperty(DCTerms.references, m.createResource("https://doi.org/10.1038/s41597-020-0528-1"))
                    .addProperty(DCTerms.creator, m.createResource("https://orcid.org/0000-0001-7323-5300"))
                    .addProperty(PROVO.wasGeneratedBy, Activity)
                    .addProperty(DCTerms.publisher, m.createResource("https://ror.org/05qghxh33"))
                    .addProperty(DCTerms.publisher, m.createResource("https://ror.org/01882y777"));
            list.stream()
                .filter(p->{
                    if (p.polygon.npoints>0) {
                        return true;
                    }
                    System.out.println(io.name+" --> NULL POLYGON DETECTED...SKIPPING");
                    return false;
                })
                .forEach(p->{
                    Resource SpatialObject = m.createResource()
                            .addProperty(RDF.type, GEO.Feature);
                    Resource geometry = m.createResource()
                            .addLiteral(GEO.asWKT, Polygon2WKT(p.polygon));
                    SpatialObject.addProperty(GEO.hasGeometry, geometry);                    
                    SpatialObjectCollection.addProperty(RDFS.member, SpatialObject);
                    Resource measurement = m.createResource().addLiteral(HAL.hasProbability,1.0f).addProperty(HAL.classification, SNO.NuclearMaterial);
                    SpatialObject.addProperty(HAL.classification, SNO.NuclearMaterial);
                    SpatialObject.addProperty(HAL.measurement, measurement);
                });
            String rah = new File(polygons).getName();
            rah = rah.substring(0,rah.length()-".svs.tar.gz".length());
            rah = rah+".ttl.gz";
            File f = Path.of(destination,rah).toFile();
            try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(f))) {
                RDFWriterBuilder rwb = RDFWriter.create();
                rwb
                .source(m)
                .base("")
                .set(RIOT.symTurtleOmitBase, true)
                .lang(Lang.TURTLE);
                RDFWriter rw = rwb.build();
                rw.output(gos);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(NS2GS.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(NS2GS.class.getName()).log(Level.SEVERE, null, ex);
            }        
        }

        @Override
        public Model call() {
            System.out.println("Processing : "+polygons+"  "+name+" ---> "+tcga.meta.containsKey(name));
            Path ee = Path.of(polygons);
            if (!tcga.meta.containsKey(name)) {
                System.out.println("MISSING ARCHIVE : "+polygons+"  "+ee+"  "+name+" ---> "+tcga.meta.containsKey(name));
            } else {
                ImageObject io = tcga.meta.get(name);
                try (TarArchiveInputStream tarInput = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(polygons))))) {
                    TarArchiveEntry ce;
                    while ((ce = tarInput.getNextTarEntry()) != null) {
                        if (ce.isFile()) {
                            ns.Protocol4(io,tarInput); 
                        }
                    }
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(NS2GS.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(NS2GS.class.getName()).log(Level.SEVERE, null, ex);
                }
                MainProcess(io);
            }
            return null;
        }
    }
}
