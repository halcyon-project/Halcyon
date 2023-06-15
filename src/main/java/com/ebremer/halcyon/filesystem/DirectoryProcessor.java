package com.ebremer.halcyon.filesystem;

import com.ebremer.halcyon.datum.DataCore;
import com.ebremer.halcyon.datum.EB;
import com.ebremer.halcyon.datum.Scan;
import com.ebremer.ns.HAL;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class DirectoryProcessor {
    public static final int DICOM = 0;
    public static final int HL7 = 1;
    public static final int XML = 2;
    public static final int CSV = 3;
    public static final int ZIP = 4;
    public static final int SVS = 5;
    public static final int TIF = 6;
    public static final int NDPI = 7;
    public static final int DFIX = 1701;
    public final int cores;
    private final Dataset buffer;
    private CopyOnWriteArrayList list = null;
    
    public DirectoryProcessor(Dataset buffer) {
        this.buffer = buffer;
        list = GetExisting();
        cores = 4;
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
        try {
            Stream<Path> yay = Files.walk(src);
            ForkJoinPool fjp = null;
            try {
                fjp = new ForkJoinPool(cores);
                fjp.submit(()->yay.collect(toList()).parallelStream()
                    .filter(Objects::nonNull)
                    .filter(fff -> {
                        return fff.toFile().isFile();
                    })
                    .filter(fff -> {
                        String v = fff.toFile().toString();
                        for (String ext1 : ext) {
                            if (v.toLowerCase().endsWith(ext1)) {
                                if (!list.contains(EB.fix(fff.toFile()))) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    })
                    .forEach((Path e) -> {
                        String xxx = EB.fix(e.toFile());
                        FileMetaExtractor fe = new FileMetaExtractor(e.toFile());
                        Model m = fe.getDataset().getNamedModel(xxx);
                        m.createResource(xxx).addProperty(RDF.type, HAL.FileManagerArtifact);
                        buffer.begin(ReadWrite.WRITE);
                        buffer.getDefaultModel().add(fe.getCoreMeta());
                        Model pathinfo = PathInfo(xxx);
                        buffer.addNamedModel(HAL.CollectionsAndResources, pathinfo);
                        buffer.addNamedModel(xxx, m);
                        buffer.commit();
                        buffer.end();
                        System.out.println("Processed : "+xxx);
                    })
                ).get();
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(DirectoryProcessor.class.getName()).log(Level.WARNING, null, ex);
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
            case DICOM -> new String[] {"dcm"};
            case HL7 -> new String[] {"txt"};
            case XML -> new String[] {"xml", "xml.gz"};
            case CSV -> new String[] {"csv"};
            case SVS -> new String[] {"svs"};
            case TIF -> new String[] {"tif"};
            case NDPI -> new String[] {"ndpi"};
            default -> null;
        };
    }
    
    public final CopyOnWriteArrayList GetExisting() {
        CopyOnWriteArrayList cur = new CopyOnWriteArrayList();
        DataCore dc = DataCore.getInstance();
        Dataset ds = dc.getDataset();
        try {
            ds.begin(ReadWrite.READ);
            QueryExecution qe = QueryExecutionFactory.create("select distinct ?g where {graph ?g {?g ?p ?o}}",ds);
            ResultSet results = qe.execSelect().materialise();
            while (results.hasNext()) {
                QuerySolution sol = results.nextSolution();
                cur.add(sol.get("g").toString());
            }
        } finally {
            ds.end();
        }
        return cur;
    }
}
