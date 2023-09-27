package com.ebremer.halcyon.converters;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.ebremer.beakgraph.rdf.BeakWriter;
import com.ebremer.halcyon.lib.HalcyonSettings;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import com.ebremer.rocrate4j.ROCrate;
import com.ebremer.rocrate4j.ROCrate.ROCrateBuilder;
import com.ebremer.rocrate4j.writers.ZipWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.arrow.memory.OutOfMemoryException;
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
        try {
            ROCrateBuilder builder = new ROCrate.ROCrateBuilder(new ZipWriter(dest));
            try (BeakWriter bw = new BeakWriter(builder, "halcyon")) {
                Resource rde = builder.getRDE();
                Model m = Engine.Load(source,builder.getRDE());
                Engine engine = new Engine(m, optimize);  
                bw.Register(m);
                bw.CreateDictionary();
                engine.HilbertPhase(bw);
                System.out.println("Process Triples : "+m.size());
                bw.Add(m);
                bw.Create(builder);
                rde.getModel().add(Engine.getMeta(m, rde));
                Model whack = Engine.getHalcyonFeatures(m, rde);
                rde.getModel().add(whack);
                rde
                    .addProperty(RDF.type, HAL.HalcyonROCrate)
                    .addLiteral(EXIF.width,engine.getWidth())
                    .addLiteral(EXIF.height,engine.getHeight());
            } catch (java.lang.IllegalStateException ex) {
                System.out.println("AA : "+ex.toString());
            } catch (OutOfMemoryException ex) {
                System.out.println("BB : "+ex.toString());
                Logger.getLogger(Ingest.class.getName()).log(Level.SEVERE, null, ex);
            }
            builder.build();
        } catch (IOException ex) {
            System.out.println("File does not exist : "+source.toString());
        }
    }
    
    public void Process(int cores, File src, boolean optimize, File dest) throws FileNotFoundException, IOException { 
        ThreadPoolExecutor engine = new ThreadPoolExecutor(cores,cores,0L,TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        engine.prestartAllCoreThreads();
        if (!dest.exists()) {
            dest.mkdirs();
        }
        if (src.isDirectory()) {
            Path path = src.toPath();
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
                    nnd = Path.of(dest.toString(),src.toPath().relativize(nnd).toString());
                    File nndf = nnd.toFile();
                    if (!nndf.exists()) {
                        Callable<Model> worker = new FileProcessor(p.toFile(), nnd.toFile(), optimize);
                        engine.submit(worker);
                    } else {
                        System.out.println("Already exists : "+nndf.toString());
                    }
                });
            System.out.println("All jobs submitted.");
            int totaljobs = engine.getQueue().size()+engine.getActiveCount();
            engine.shutdown();
            while (!engine.isTerminated()) {
                int c = engine.getQueue().size()+engine.getActiveCount();
                long ram = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1024L/1024L;
                System.out.println("file jobs completed : "+(totaljobs-c)+" remaining file jobs: "+c+"  Total RAM used : "+ram+"MB  Maximum RAM : "+(Runtime.getRuntime().maxMemory()/1024L/1024L)+"MB");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Ingest.class.getName()).log(Level.SEVERE, null, ex);
                }
            }  
            System.out.println("Engine shutdown.");
        } else if (src.toString().endsWith(".ttl.gz")) {
            OneFile(src, dest, optimize);
        }
    }
   
    public static void main(String[] args) throws FileNotFoundException, IOException {     
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.OFF);
        IngestParameters params = new IngestParameters();   
        JCommander jc = JCommander.newBuilder().addObject(params).build();
        jc.setProgramName("ingest");    
        try {
            jc.parse(args);
            if (params.isHelp()) {
                jc.usage();
                System.exit(0);
            } else {
                if (params.src.exists()) {
                    new Ingest().Process(params.threads, params.src, params.optimize, params.dest);
                } else {
                    System.out.println("Source does not exist! "+params.src);
                }
            }
        } catch (ParameterException ex) {
            if (params.version) {
                System.out.println("ingest - Version : "+HalcyonSettings.VERSION);
            } else {
                System.out.println(ex.getMessage());
            }
        }
    }
    
    class FileProcessor implements Callable<Model> {
        private final File source;
        private final File dest;
        private final boolean optimize;

        public FileProcessor(File source, File dest, boolean optimize) {
            this.source = source;
            this.dest = dest;
            this.optimize = optimize;
        }
        
        @Override
        public Model call() throws Exception {
            OneFile(source, dest, optimize);
            return null;
        }
    }
}
