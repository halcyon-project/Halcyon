package com.ebremer.halcyon.converters;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.ebremer.halcyon.lib.HalcyonSettings;
import com.ebremer.halcyon.utils.ImageMeta;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.RIOT;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class HoverNet2OA {
    public static final String NAME = "HoverNet2OA";
    public final ImageMeta im;
    public final HashMap<String,String> clazz;
    public final HashMap<String,String> colors;
    
    public HoverNet2OA() {
        im = new ImageMeta();
        clazz = new HashMap<>(6);
        clazz.put("0", "urn:sbu:nolabel");
        clazz.put("1", "urn:sbu:neoplastic");
        clazz.put("2", "urn:sbu:inflammatory");
        clazz.put("3", "urn:sbu:connective");
        clazz.put("4", "urn:sbu:necrosis");
        clazz.put("5", "urn:sbu:no-neoplastic");
        colors = new HashMap<>(6);
        colors.put("urn:sbu:nolabel","000000");
        colors.put("urn:sbu:neoplastic","ff0000");
        colors.put("urn:sbu:inflammatory","00ff00");
        colors.put("urn:sbu:connective","0000ff");
        colors.put("urn:sbu:necrosis","ffff00");
        colors.put("urn:sbu:no-neoplastic","ffa500");
    }
        
    public static String getWKT(JsonArray ja) {
        StringBuilder sb = new StringBuilder();
        sb.append("POLYGON ((");
        ja.forEach(jv->{
            JsonArray point = (JsonArray) jv;
            int x = point.getInt(0);
            int y = point.getInt(1);
            sb.append(x).append(" ").append(y).append(", ");
        });
        sb.append(ja.get(0).asJsonArray().getInt(0)).append(" ").append(ja.get(0).asJsonArray().getInt(1)).append("))");
        return sb.toString();
    }
    
    public static Model getBase(String md5) {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("exif", EXIF.NS);
        m.setNsPrefix("hal", HAL.NS);
        m.setNsPrefix("dcmi", DCTerms.NS);
        m.setNsPrefix("so", SchemaDO.NS);
        m.setNsPrefix("oa", OA.NS);
        m.setNsPrefix("rdf", RDF.getURI());
        m.setNsPrefix("xsd", XSD.NS);
        m.setNsPrefix("geos", "http://www.opengis.net/ont/geosparql#");
        Resource CA = m.createResource("https://bmi.stonybrookmedicine.edu/saarthak/2023/07/12")
                .addProperty(RDF.type, SchemaDO.CreateAction)
                .addProperty(SchemaDO.description, "Hovernet Cell segmentation and classification")
                .addProperty(SchemaDO.name, "Hovernet Cell segmentation and classification")
                .addProperty(SchemaDO.object, m.createResource("urn:md5:"+md5))
                .addProperty(SchemaDO.instrument, m.createResource("https://github.com/vqdang/hover_net"))
                .addProperty(SchemaDO.result, m.createResource(""));
        m.createResource("")
                .addProperty(RDF.type, SchemaDO.Dataset)
                .addProperty(RDF.type, HAL.HalcyonROCrate)
                .addProperty(SchemaDO.name, "Hovernet Cell segmentation and classification")
                .addProperty(SchemaDO.description, "Hovernet Cell segmentation and classificationn")
                .addProperty(SchemaDO.creator, m.createResource("http://orcid.org/0000-0002-5426-4111"))
                .addProperty(SchemaDO.publisher, m.createResource("https://ror.org/05qghxh33"))
                .addProperty(SchemaDO.publisher, m.createResource("https://ror.org/01882y777"))
                .addProperty(SchemaDO.license, m.createResource("https://creativecommons.org/licenses/by-nc-sa/3.0/au/"))
                .addProperty(HAL.hasCreateAction, CA);
        return m;
    }
    
    public static void DumpModel(Model m, Path file, boolean compress) {
        if (!file.getParent().toFile().exists()) file.getParent().toFile().mkdirs();
        try {
            OutputStream fos;
            if (compress) {
                fos = new GZIPOutputStream(new FileOutputStream(file.toFile()+".gz"));
            } else {
                fos = new FileOutputStream(file.toFile());
            }
            try (fos) {
                Context context = new Context();
                context.setTrue(RIOT.symTurtleOmitBase);
                RDFWriter.create()
                    .source(m)
                    .context(context)
                    .lang(Lang.TURTLE)
                    .base("")
                    .output(fos); 
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Legacy.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Legacy.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void Process(File file, File dest) {
        String name = file.getName().replace(".json.gz", ".svs").toUpperCase();
        System.out.println(name);
        ImageMeta.ImageObject io = im.meta.get(name);
        System.out.println("===> "+io.height+" X "+io.width);
        Model m = getBase(io.md5);
        m.setNsPrefix("oa", OA.NS);
        m.setNsPrefix("xsd", XSD.NS);
        m.setNsPrefix("sno", "http://snomed.info/id/");
        m.setNsPrefix("opengis", "http://www.opengis.net/ont/geosparql#");
        Resource img = m.createResource("urn:md5:"+io.md5)
                .addLiteral(EXIF.width, io.width)
                .addLiteral(EXIF.height, io.height);
        try (InputStream fis = new GZIPInputStream(new FileInputStream(file));
            JsonReader jsonReader = Json.createReader(fis)) {
            JsonObject jo = jsonReader.readObject().getJsonObject("nuc");
            jo.forEach((k,v)->{
                JsonObject p = (JsonObject) v;
                JsonArray poly = p.getJsonArray("contour");
                float prob = (float) p.getJsonNumber("type_prob").doubleValue();
                String type = clazz.get(String.valueOf(p.getInt("type")));
                Resource body = m.createResource()
                        .addProperty(RDF.type, HAL.ProbabilityBody)
                        .addProperty(HAL.assertedClass, m.createResource(type))
                        .addLiteral(HAL.hasCertainty, prob);
                Resource selector = m.createResource()
                        .addProperty(RDF.type, OA.FragmentSelector)
                        .addProperty(DCTerms.conformsTo, m.createResource("http://www.opengis.net/ont/geosparql#wktLiteral"))
                        .addProperty(RDF.value, getWKT(poly))
                        .addProperty(OA.hasSource, img);
                m.createResource()
                    .addProperty(RDF.type, OA.Annotation)
                    .addProperty(OA.hasBody, body)
                    .addProperty(OA.hasSelector, selector);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        colors.forEach((k,v)->{
            m.createResource(k).addProperty(HAL.color, v);
        });
        DumpModel(m,dest.toPath(),true);
    }
    
    public void Traverse(HoverNet2OAParameters params) {
        try {
            ThreadPoolExecutor engine = new ThreadPoolExecutor(params.threads,params.threads,0L,TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
            engine.prestartAllCoreThreads();
            Files.list(params.src.toPath())
                    .filter(p->{
                        return p.toFile().getName().toLowerCase().endsWith(".json.gz");
                    })
                    .forEach(p->{
                        String nd = p.toFile().getName().replace(".json.gz",".ttl");
                        Callable<Model> worker = new FileProcessor(p.toFile(), Path.of(params.dest.toString(),nd).toFile());
                        engine.submit(worker);                       
                    });
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
                    Logger.getLogger(NS2OA.class.getName()).log(Level.SEVERE, null, ex);
                }
            }  
            System.out.println("Engine shutdown");
        } catch (IOException ex) {
            Logger.getLogger(HoverNet2OA.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void main(String[] args) {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.OFF);
        HoverNet2OAParameters params = new HoverNet2OAParameters();   
        JCommander jc = JCommander.newBuilder().addObject(params).build();
        jc.setProgramName(NAME);
        try {
            jc.parse(args);
                if (params.isHelp()) {
                    jc.usage();
                    System.exit(0);
                } else {
                    HoverNet2OA hn2oa = new HoverNet2OA();
                    if (params.src.exists()) {
                        if (params.src.isDirectory()) {
                            if (params.dest.exists()) {
                                if (!params.dest.isDirectory()) {
                                    params.dest.delete();
                                }
                            } else {
                                params.dest.mkdirs();
                            }
                            hn2oa.Traverse(params);
                        } else {
                            
                        }
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
        private final File file;
        private final File dest;
        
        public FileProcessor(File file, File dest) {
            this.dest = dest;
            this.file = file;
        }

        @Override
        public Model call() throws Exception {
            Process(file, dest);
            return null;
        }
    }
}
