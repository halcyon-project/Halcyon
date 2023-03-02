package com.ebremer.halcyon.filesystem;

import com.ebremer.halcyon.datum.EB;
import com.ebremer.halcyon.datum.Scan;
import com.ebremer.ns.HAL;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class LegacyDirectoryProcessor {
    public static final int DICOM = 0;
    public static final int HL7 = 1;
    public static final int XML = 2;
    public static final int CSV = 3;
    public static final int ZIP = 4;
    public static final int SVS = 5;
    public static final int TIF = 6;
    public static final int NDPI = 7;
    public static final int DFIX = 1701;
    public int cores = 1;
    private final Dataset buffer;
    
    public LegacyDirectoryProcessor(Dataset buffer) {
        this.buffer = buffer;
    }    
    
    public LegacyDirectoryProcessor(Dataset buffer, int numcores) {
        this.buffer = buffer;
        cores = numcores;
    }
    
    public Model PathInfo(String s) {
        Model m = ModelFactory.createDefaultModel();
        try {
            URI childuri = new URI(s);
            Path childpath = Path.of(childuri);
            Path rootpath = childpath.getRoot();
            int pd = rootpath.getNameCount();
            int nc = childpath.getNameCount();
            for (int i=0; i<nc-pd; i++) {
                Resource parent = m.createResource(childpath.getParent().toUri().toString());
                Resource child = m.createResource(childpath.toUri().toString());
                m.add(parent, SchemaDO.hasPart, child);
                m.add(parent, RDF.type, SchemaDO.Dataset);
                m.add(child, SchemaDO.isPartOf, parent);
                childpath = childpath.getParent();
            }
        } catch (URISyntaxException ex) {
            Logger.getLogger(Scan.class.getName()).log(Level.SEVERE, null, ex);
        }
        return m;
    }

    public void Traverse(Path src, String[] ext, int ftype) {
        System.out.println("Traverse : "+src.toString()+" EXT : "+ext.length+" ftype : "+ftype);
        try {
            Stream<Path> yay = Files.walk(src);
            ForkJoinPool fjp = null;
            try {
                fjp = new ForkJoinPool(cores);
                fjp.submit(()->yay.collect(toList()).parallelStream()
                    .filter(Objects::nonNull)
                    .filter(fff -> {
                        String v = fff.toFile().toString();
                        for (String ext1 : ext) {
                            if (v.toLowerCase().endsWith(ext1)) {
                                return true;
                            } else {
                                return false;
                            }
                        }
                        return false;
                    })
                    .forEach((Path e) -> {
                       System.out.println("Processing : "+e.toString());
                        String xxx = EB.fix(e.toFile());
                        FileMetaExtractor fe = new FileMetaExtractor(e.toFile());
                        Model m = fe.getDataset().getNamedModel(xxx);
                        if (m!=null) {
                            buffer.begin(ReadWrite.WRITE);
                            buffer.getDefaultModel().add(fe.getCoreMeta());
                            Model pathinfo = PathInfo(xxx);
                            buffer.addNamedModel(HAL.CollectionsAndResources, pathinfo);
                            buffer.addNamedModel(xxx, m);
                            buffer.commit();
                            buffer.end();                            
                        } else {
                            System.out.println("NULLLLLLLLLLLLLLLLL");
                        }
                    })).get();
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(DirectoryProcessor.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                if (fjp != null) {
                    fjp.shutdown();
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(DirectoryProcessor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
     
    public static String[] GetExtensions(int ftype) {
        return switch (ftype) {
            case ZIP -> new String[] {"zip"};
            case DICOM -> new String[] {"dcm", "dat"};
            case DFIX -> new String[] {"dcm", "dat"};
            case HL7 -> new String[] {"txt"};
            case XML -> new String[] {"xml", "xml.gz"};
            case CSV -> new String[] {"csv"};
            case SVS -> new String[] {"svs"};
            case TIF -> new String[] {"tif"};
            case NDPI -> new String[] {"ndpi"};
            default -> null;
        };
    }
    
    public static void main(String[] args) throws FileNotFoundException {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)(org.slf4j.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.OFF);
       // String[] tmp = {"D:\\HalcyonStorage"};
       // args = tmp;
        Dataset ds = DatasetFactory.createTxnMem();
        LegacyDirectoryProcessor dp = new LegacyDirectoryProcessor(ds);
        dp.Traverse(Path.of(args[0]), GetExtensions(DirectoryProcessor.TIF), DirectoryProcessor.TIF);
        String output = args[0]+File.separatorChar+"images.trig";
        System.out.println("OUTPUT : "+output);
        FileOutputStream fos = new FileOutputStream(new File(output));
        ds.begin();
        RDFDataMgr.write(fos, ds, RDFFormat.TRIG_PRETTY);
        ds.end();
        System.out.println("DONE.");
    }
}