package com.ebremer.halcyon.converters;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.ebremer.beakgraph.ng.BG;
import com.ebremer.beakgraph.ng.SpecialProcess;
import com.ebremer.halcyon.lib.shacl.GeoSPARQL;
import com.ebremer.halcyon.raptor.HeatmapProcess;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.raptor.SegmentationProcess;
import com.ebremer.halcyon.raptor.HilbertSpecial;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.GEO;
import com.ebremer.ns.HAL;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.XSD;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class Ingest {
    private final ArrayList<BG.PropertyAndDataType> list;
    private final ArrayList<SpecialProcess> specials;
    private static final Logger logger = LoggerFactory.getLogger(Ingest.class);

    public Ingest() {
        list = new ArrayList<>();
        list.add(new BG.PropertyAndDataType(HAL.low.getURI(), XSD.xlong));
        list.add(new BG.PropertyAndDataType(HAL.high.getURI(), XSD.xlong));
        list.add(new BG.PropertyAndDataType(HAL.hasRange.getURI(), null)); 
        list.add(new BG.PropertyAndDataType(HAL.asHilbert.getURI(), null)); 
        //list.add(new BG.PropertyAndDataType(GEO.hasPerimeterLength.getURI(), XSD.xdouble));
        //list.add(new BG.PropertyAndDataType(GEO.hasArea.getURI(), XSD.xdouble));
        specials = new ArrayList<>();
        specials.add(new HilbertSpecial());
    }
           
    public void OneFile(File source, File dest, IngestParameters params) {
        Dataset dsi = DatasetFactory.create();
        try (
            FileInputStream fis = new FileInputStream(source);
            GZIPInputStream gis = new GZIPInputStream(fis);
        ) {
            RDFDataMgr.read(dsi.getDefaultModel(), gis, Lang.TURTLE);
            boolean valid = true;
            if (params.validate) {
                GeoSPARQL geo = new GeoSPARQL();
                valid = geo.validate(dsi.getDefaultModel());
            }
            if (valid) {
                Pair pair = getImageSize(dsi.getDefaultModel());
                if (!params.optimize) {
                    logger.debug("Segmentation Type");
                    BG.getBuilder()
                        .dataset(dsi)
                        .handle(list)
                        .setProcess(new SegmentationProcess(pair.width(),pair.height(),512,512))
                        .Extra(specials)
                        .file(dest)
                        .build();
                } else {
                    logger.debug("Heatmap Type");
                    BG.getBuilder()
                        .dataset(dsi)
                        .handle(list)
                        .setProcess(new HeatmapProcess(pair.width(),pair.height()))
                    // .Extra(specials)
                        .file(dest)
                        .build();
                }
            } else {
                logger.info("GeoSPARQL format not valid --> "+source.toString());
            }
        } catch (FileNotFoundException ex) {
            logger.error(ex.toString());
        } catch (IOException ex) {
            logger.error(ex.toString());
        }
    }
    
    private record Pair(int width, int height) {};
    
    private Pair getImageSize(Model m) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?height ?width
            where {
                ?image a so:ImageObject; exif:width ?width; exif:height ?height
            } limit 1
            """
        );
        pss.setNsPrefix("geo", GEO.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),m);
        ResultSet rs = qe.execSelect();
        if (rs.hasNext()) {
            QuerySolution qs = rs.next();
            int width = qs.get("width").asLiteral().getInt();
            int height = qs.get("height").asLiteral().getInt();
            return new Pair(width,height);
        }
        throw new Error("ImageObject not found in source file");
    }
    
    public void Process(int cores, File src, File dest, IngestParameters params) throws FileNotFoundException, IOException { 
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
                        Callable<Model> worker = new FileProcessor(p.toFile(), nnd.toFile(), params);
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
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    logger.error(ex.toString());
                }
            }  
            System.out.println("Engine shutdown.");
        } else if (src.toString().endsWith(".ttl.gz")) {
            OneFile(src, dest, params);
        }
    }
   
    public static void main(String[] args) throws FileNotFoundException, IOException {
        //Configurator.setRootLevel(Level.ERROR);
        //Configurator.setLevel("com.ebremer.halcyon.raptor", Level.ERROR);
        //Configurator.setLevel("com.ebremer.beakgraph", Level.ERROR);      
        // monitor https://issues.apache.org/jira/browse/LOG4J2-2649
        logger.info("ingest "+Arrays.toString(args));
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
                    new Ingest().Process(params.threads, params.src, params.dest, params);
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
        private final IngestParameters params;

        public FileProcessor(File source, File dest, IngestParameters params) {
            this.params = params;
            this.source = source;
            this.dest = dest;
        }
        
        @Override
        public Model call() throws Exception {
            OneFile(source, dest, params);
            return null;
        }
    }
}
