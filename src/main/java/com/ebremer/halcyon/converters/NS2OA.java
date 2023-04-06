package com.ebremer.halcyon.converters;

import com.ebremer.halcyon.ExtendedPolygon;
import com.ebremer.halcyon.ExtendedPolygons;
import com.ebremer.halcyon.utils.ImageMeta;
import com.ebremer.halcyon.utils.ImageMeta.ImageObject;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import com.ebremer.ns.SNO;
import java.awt.Polygon;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.UUID;
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
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.RDFWriterBuilder;
import org.apache.jena.riot.RIOT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.XSD;

/**
 *
 * @author erich
 */
public class NS2OA {
    private final ThreadPoolExecutor engine;
    
    public NS2OA(int cores) {
        engine = new ThreadPoolExecutor(cores,cores,0L,TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        engine.prestartAllCoreThreads();
    }
    
    public void shutdown() {
        System.out.println("All jobs submitted.");
        int totaljobs = engine.getQueue().size()+engine.getActiveCount();
        while (engine.getActiveCount()>0) {
            int c = engine.getQueue().size()+engine.getActiveCount();
            long ram = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1024L/1024L;
            System.out.println("jobs completed : "+(totaljobs-c)+" remaining jobs: "+c+"  Total RAM used : "+ram+"MB  Maximum RAM : "+(Runtime.getRuntime().maxMemory()/1024L/1024L)+"MB");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(NS2OA.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        System.out.println("Engine shutdown");
        engine.shutdown();
    }
    
    public void Turbo(ImageMeta tcga, String base, String name, String destination) throws IOException {
        Callable<Model> worker = new FileProcessor(tcga, base, name, destination);
        engine.submit(worker);
    }
    
     public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException, IOException {
        String base = args[0].trim();
        String type = args[1].trim();
        NS2OA ns2oa;
        if (args.length==3) {
            ns2oa = new NS2OA(Integer.parseInt(args[2]));
        } else {
            ns2oa = new NS2OA(1);
        }
        String destination = Path.of(Path.of(base).getParent().toString(),"rdf",type).toString();
        File ddd = Path.of(destination).toFile();
        if (!ddd.exists()) {
            ddd.mkdirs();
        }
        ImageMeta tcga = new ImageMeta();
        Files.list(Path.of(base))            
            .forEach(p->{
                //Path t = Path.of(base, p.getFileName().toString(), p.getFileName().toString());
                Path t = Path.of(base, p.getFileName().toString(), p.getFileName().toString());
                String name = p.getFileName().toString();
                name = name.substring(0, name.length()-".tar.gz".length());
                try {            
                    ns2oa.Turbo(tcga, t.toString(), name, destination);
                } catch (IOException ex) {
                    Logger.getLogger(NS2OA.class.getName()).log(Level.SEVERE, null, ex);
                }
        });
        ns2oa.shutdown();
    }
    
    class FileProcessor implements Callable<Model> {
        private final ImageMeta tcga;
        private final String polygons;
        private final String destination;
        private final String name;
        private final NeoSegmentations ns;
        private static final String header =
                """
                @prefix oa:  <http://www.w3.org/ns/oa#> .
                @prefix sno: <http://snomed.info/id/> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                @prefix so: <https://schema.org/> .
                @prefix hal: <https://www.ebremer.com/halcyon/ns/> .
                @prefix exif: <http://www.w3.org/2003/12/exif/ns#> .
                       
                <> so:creator <http://orcid.org/0000-0003-0223-1059>;
                    so:keywords "Whole Slide Imaging","pathology","nuclear segmentation";
                    so:datePublished "2022-09-16 16:05:56";
                    so:description "Nuclear segmentation of TCGA cancer types";
                    so:license <https://creativecommons.org/licenses/by-nc-sa/3.0/au/>;
                    so:name "cnn-nuclear-segmentations-2019";
                    hal:hasCreateAction <https://bmi.stonybrookmedicine.edu/nuclearsegmentation/tcga/2019>;
                    a so:Dataset, hal:HalcyonROCrate;
                    so:publisher <https://ror.org/05qghxh33>, <https://ror.org/01882y777> .
                        
                    <https://bmi.stonybrookmedicine.edu/nuclearsegmentation/tcga/2019> a so:CreateAction;
                        so:name "cnn-nuclear-segmentations-2019";
                        so:description "cnn-nuclear-segmentations-2019";
                        so:instrument <https://github.com/SBU-BMI/quip_cnn_segmentation/releases/tag/v1.1>;
                        so:result <> .
                """;

        public FileProcessor(ImageMeta tcga, String polygons, String name, String destination) {
            this.tcga = tcga;
            this.polygons = polygons;
            this.destination = destination;
            this.name = name.toUpperCase();
            this.ns = new NeoSegmentations();
        }
        
        public static String Polygon2SVG(Polygon p) {
            String s = "<polygon points=";
            for (int i=0; i<p.npoints; i++) {
                s = s + p.xpoints[i]+","+p.ypoints[i]+" ";
            }
            s = s.trim()+"/>";
            return s;
        }
    /*
        public static String Polygon2WKT(Polygon p) {
            String s = "POLYGON ((";
            for (int i=0; i<p.npoints; i++) {
                s = s + p.xpoints[i]+" "+p.ypoints[i]+", ";
            }
        sb
            .append(p.xpoints[0])
            .append(" ")
            .append(p.ypoints[0])
            .append("))");
            return s;
        } 
      */  
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
            LinkedList<ExtendedPolygon> list = eps2.GetPolygons();
            m.setNsPrefix("oa", OA.NS);
            m.setNsPrefix("xsd", XSD.NS);
            m.setNsPrefix("sno", "http://snomed.info/id/");
            list.stream()
                .filter(p->{
                    if (p.polygon.npoints>0) {
                        return true;
                    }
                    System.out.println(io.name+" --> NULL POLYGON DETECTED...SKIPPING");
                    return false;
                })
                .forEach(p->{
                    Resource a = ResourceFactory.createResource("urn:uuid:"+UUID.randomUUID().toString());
                    m.add(a,RDF.type,OA.Annotation);
                    Resource s = m.createResource();
                    m.add(s,RDF.type,OA.FragmentSelector);
                    Resource image = m.createResource("urn:md5:"+io.md5);
                    m.add(s,OA.hasSource,image);
                    m.addLiteral(image,EXIF.width,io.width);
                    m.addLiteral(image,EXIF.height,io.height);
                    Resource ca = m.createResource("https://bmi.stonybrookmedicine.edu/nuclearsegmentation/tcga/2019");
                    m.add(ca, SchemaDO.object, image);
                    m.add(s,DCTerms.conformsTo,m.createResource("http://www.w3.org/TR/SVG/"));
                    m.add(s,RDF.value,Polygon2WKT(p.polygon));
                    Resource body = m.createResource();
                    m.addLiteral(body,HAL.hasCertainty,1.0f);
                    m.add(body,RDF.type, HAL.ProbabilityBody);
                    switch (p.classid) {
                        case "0" -> m.add(body,HAL.assertedClass,SNO.NuclearMaterial);
                        case "1" -> m.add(body,HAL.assertedClass,SNO.Lymphocytes);
                        case "2" -> m.add(body,HAL.assertedClass,SNO.TumorCell);
                        case "3" -> m.add(body,HAL.assertedClass,SNO.Cell);
                        default -> m.add(body,HAL.assertedClass,SNO.Unknown);
                    }
                    m.add(a,OA.hasBody,body);
                    m.add(a,OA.hasSelector,s);                        
                });
            String rah = new File(polygons).getName();
            rah = rah.substring(0,rah.length()-".svs.tar.gz".length());
            rah = rah+".ttl.gz";
            File f = Path.of(destination,rah).toFile();
            Model head = ModelFactory.createDefaultModel();
            try (InputStream is = new ByteArrayInputStream(header.getBytes())) {
                RDFDataMgr.read(head, is, Lang.TURTLE);
                m.add(head);
            } catch (IOException ex) {
                Logger.getLogger(NS2OA.class.getName()).log(Level.SEVERE, null, ex);
            }
            try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(f))) {
                RDFWriterBuilder rwb = RDFWriter.create(m);
                rwb
                .base("")
                .set(RIOT.symTurtleOmitBase, true)
                .lang(Lang.TURTLE);
                RDFWriter rw = rwb.build();
                rw.output(gos);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(NS2OA.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(NS2OA.class.getName()).log(Level.SEVERE, null, ex);
            }        
        }

        @Override
        public Model call() {
            System.out.println("CALL : "+polygons+"  "+name+" ---> "+tcga.meta.containsKey(name));
            Path ee = Path.of(polygons);
            if (!tcga.meta.containsKey(name)) {
                System.out.println("MISSING ARCHIVE : "+polygons+"  "+ee+"  "+name+" ---> "+tcga.meta.containsKey(name));
            } else {
                ImageObject io = tcga.meta.get(name);
                try (TarArchiveInputStream tarInput = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(polygons))))) {
                    TarArchiveEntry ce;
                    while ((ce = tarInput.getNextTarEntry()) !=null) {
                        if (ce.isFile()) {
                            ns.Protocol4(io,tarInput); 
                        }
                    }
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(NS2OA.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(NS2OA.class.getName()).log(Level.SEVERE, null, ex);
                }
                MainProcess(io);
            }
            return null;
        }
    }
}
