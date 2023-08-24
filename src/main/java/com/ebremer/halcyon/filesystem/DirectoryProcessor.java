package com.ebremer.halcyon.filesystem;

import com.ebremer.halcyon.datum.DataCore;
import com.ebremer.halcyon.datum.URITools;
import com.ebremer.halcyon.filereaders.FileReader;
import com.ebremer.halcyon.filereaders.FileReaderFactory;
import com.ebremer.halcyon.filereaders.FileReaderFactoryProvider;
import com.ebremer.halcyon.lib.HalcyonSettings;
import com.ebremer.halcyon.lib.ImageReader;
import com.ebremer.halcyon.utils.HashTools;
import com.ebremer.ns.HAL;
import com.ebremer.ns.LOC;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class DirectoryProcessor {
    public final int cores;
    private final Dataset buffer;
    private final CopyOnWriteArrayList<Resource> list;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public DirectoryProcessor(Dataset buffer) {
        this.buffer = buffer;
        list = GetExisting();
        cores = 4;
    }
    
    public Model PathInfo(String s) {
        Model m = ModelFactory.createDefaultModel();
        URI childuri = (new File(s)).toURI();
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
        return m;
    }

    public void Traverse(Path src) {
        try {
            Stream<Path> yay = Files.walk(src);
            ForkJoinPool fjp = null;
            try {
                fjp = new ForkJoinPool(cores);
                fjp.submit(()->yay.collect(toList()).parallelStream()
                    .filter(Objects::nonNull)
                    .filter(fff -> fff.toFile().isFile())
                    .map(path->path.toUri())
                    .map(path-> ResourceFactory.createResource(URITools.fix(path.toString())))
                    .filter(fff -> !list.contains(fff))
                    .filter(fff->FileReaderFactoryProvider.hasReaderFor(fff))
                    .forEach(r -> {
                        FileReaderFactory frf = FileReaderFactoryProvider.getReaderForFormat(r);
                        Model m;
                        try (FileReader fr = frf.create(new URI(r.getURI()))){
                            m = fr.getMeta();
                            m.add(r, RDF.type, HAL.FileManagerArtifact);
                            URI uri = new URI(r.getURI());
                            File file = new File(uri);
                            
                            if (fr instanceof ImageReader) {
                                long now = System.nanoTime();
                                String hash = HashTools.GetMD5(file);
                                System.out.println("Time = "+((System.nanoTime()-now)/1000000000d));
                                m.add(r,LOC.md5,hash);
                                m.add(r,OWL.sameAs,m.createResource("urn:md5:"+hash));
                            }
                            
                            ZonedDateTime dateTime = ZonedDateTime.now();
                            m.addLiteral(r, SchemaDO.datePublished, dateTime.format(formatter));
                            m.add(r, SchemaDO.name, r.getLocalName());
                            m.addLiteral(r,SchemaDO.contentSize,file.length());
                            m.add(r,SchemaDO.instrument, HalcyonSettings.HALCYONAGENT);
                            
                            Model pathinfo = PathInfo(file.getCanonicalPath());
                            //System.out.println("============================================================");
                            //RDFDataMgr.write(System.out, m, Lang.TURTLE);
                            //System.out.println("============================================================");
                            //RDFDataMgr.write(System.out, pathinfo, Lang.TURTLE);
                            buffer.begin(ReadWrite.WRITE);
                            buffer.addNamedModel(HAL.CollectionsAndResources, pathinfo);
                            buffer.addNamedModel(r, m);
                            buffer.commit();
                            buffer.end();
                            System.out.println("Processed : "+r);
                        } catch (URISyntaxException ex) {
                            Logger.getLogger(DirectoryProcessor.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IOException ex) {
                            Logger.getLogger(DirectoryProcessor.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (Exception ex) {
                            Logger.getLogger(DirectoryProcessor.class.getName()).log(Level.SEVERE, null, ex);
                        }
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
    
    public final CopyOnWriteArrayList GetExisting() {
        CopyOnWriteArrayList<Resource> cur = new CopyOnWriteArrayList<>();
        DataCore dc = DataCore.getInstance();
        Dataset ds = dc.getDataset();
        try {
            ds.begin(ReadWrite.READ);
            QueryExecution qe = QueryExecutionFactory.create("select distinct ?g where {graph ?g {?g ?p ?o}}",ds);
            ResultSet results = qe.execSelect().materialise();
            while (results.hasNext()) {
                QuerySolution sol = results.nextSolution();
                cur.add(sol.get("g").asResource());
            }
        } finally {
            ds.end();
        }
        return cur;
    }
    
    public static void main(String[] args) {
        Dataset ds = DataCore.getInstance().getDataset();
        DirectoryProcessor dp = new DirectoryProcessor(ds);
        dp.Traverse(Path.of("D:\\HalcyonStorage"));
    }
}
