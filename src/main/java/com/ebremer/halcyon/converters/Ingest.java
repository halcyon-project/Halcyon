package com.ebremer.halcyon.converters;

import com.ebremer.beakgraph.rdf.BeakWriter;
import com.ebremer.halcyon.HalcyonDefaults;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import com.ebremer.rocrate4j.ROCrate;
import com.ebremer.rocrate4j.writers.ZipWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class Ingest {
        
    public void OneFile(File source, File dest, boolean optimize) {
        Engine engine;
        try {
            ROCrate.ROCrateBuilder builder = new ROCrate.ROCrateBuilder(new ZipWriter(dest));
            Resource rde = builder.getRDE();
            Model m = Engine.Load(source,builder.getRDE());
            engine = new Engine(m, optimize);
            //try(FileOutputStream fos = new FileOutputStream(new File("/HDT/dump.ttl"));) {
              //  RDFDataMgr.write(fos, m, Lang.TURTLE);
            //}
            
            try (BufferAllocator allocator = new RootAllocator()) {
                BeakWriter bw = new BeakWriter(allocator, builder, "halcyon");
                bw.Register(m);
                bw.CreateDictionary(allocator);
                engine.HilbertPhase(bw);
                bw.Add(m);
                bw.Create(allocator);
                rde.getModel().add(Engine.getMeta(m, rde));
                Model whack = Engine.getHalcyonFeatures(m, rde);
                rde.getModel().add(whack);
                rde
                    .addProperty(RDF.type, HAL.HalcyonROCrate)
                    .addLiteral(EXIF.width,engine.getWidth())
                    .addLiteral(EXIF.height,engine.getHeight());
            } catch (java.lang.IllegalStateException ex) {
                System.out.println(ex.toString());
            }
            builder.build();
        } catch (IOException ex) {
            Logger.getLogger(Ingest.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("File does not exist : "+source.toString());
        }
    }
    
    public void Process(String source, boolean optimize, String destination) throws FileNotFoundException, IOException { 
        System.out.println("PROCESSING : "+source+" ===> "+destination);
        File src = new File(source);
        File dest = new File(destination);
        if (!dest.exists()) {
            dest.mkdirs();
        }
        if (src.isDirectory()) {
            Path path = Path.of(source);
            Files
                .walk(path)
                .filter(f->!f.toFile().isDirectory())
                .filter(f->{
                    return f.toString().endsWith(".ttl.gz");
                })
                .forEach(p->{
                    System.out.println(p);
                    String nd = p.toString();
                    nd = nd.substring(0,nd.length()-".ttl.gz".length())+".zip";
                    Path nnd = Path.of(nd);
                    nnd = Path.of(destination,src.toPath().relativize(nnd).toString());
                    File nndf = nnd.toFile();
                    if (!nndf.exists()) {
                        OneFile(p.toFile(), nnd.toFile(), optimize);
                    } else {
                        System.out.println("Already exists : "+nndf.toString());
                    }
                });
        } else if (src.toString().endsWith(".ttl.gz")) {
            OneFile(src, dest, optimize);
        }
    }
   
    public static void main(String[] args) throws FileNotFoundException, IOException {     
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.OFF);
        //String[] temp = {"D:\\luke\\load2\\rdf.gmm","D:\\luke\\load2\\zip-cluster_20_json_gmm","heatmap"};
        //String[] temp = {"D:\\tcga\\nuclearsegmentation2019\\rdf","D:\\tcga\\nuclearsegmentation2019\\zip","segmentation"};
        //args = temp;
        if (args.length == 3) {
            String src = args[0];
            String dest = args[1];
            boolean optimize = args[2].equals("heatmap");
            new Ingest().Process(src, optimize, dest);
        } else {
            System.out.println("Halcyon ----------------------------------------------------");
            System.out.println("ingest - version : "+HalcyonDefaults.VERSION);
            System.out.println("""
                                Usage:
                                ingest <source> <destination> (heatmap|segmentation)
                            """);
        }
    }
}
