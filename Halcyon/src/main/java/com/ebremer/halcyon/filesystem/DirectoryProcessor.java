package com.ebremer.halcyon.filesystem;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.filereaders.FileReader;
import com.ebremer.halcyon.filereaders.FileReaderFactory;
import com.ebremer.halcyon.filereaders.FileReaderFactoryProvider;
import com.ebremer.halcyon.filereaders.ImageReader;
import com.ebremer.halcyon.lib.URITools;
import com.ebremer.halcyon.raptor.HilbertSpecial;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
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
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class DirectoryProcessor {
    public final int cores;
    private final Dataset buffer;
    private final CopyOnWriteArrayList<Resource> list;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(HilbertSpecial.class);
    
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
                    //.filter(f->f.toString().endsWith(".zip"))
                    .map(path->path.toUri())
                    .map(path-> ResourceFactory.createResource(URITools.fix(path.toString())))
                    .filter(fff -> !list.contains(fff))
                    .filter(fff->FileReaderFactoryProvider.hasReaderFor(fff))
                    .forEach(r -> {
                        System.out.println("Processing ---> "+r);
                        FileReaderFactory frf = FileReaderFactoryProvider.getReaderForFormat(r);
                        Model m;
                        try (FileReader fr = frf.create(new URI(r.getURI()))){
                            m = fr.getMeta();
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
                            //  FIX THIS LINE WE NEED IT m.addLiteral(r, SchemaDO.datePublished, dateTime.format(formatter));
                            m.add(r, SchemaDO.name, r.getLocalName());
                            m.addLiteral(r,SchemaDO.contentSize,file.length());
                            m.add(r,SchemaDO.instrument, HalcyonSettings.HALCYONAGENT);                            
                            Model pathinfo = PathInfo(file.getCanonicalPath());
                            buffer.begin(ReadWrite.WRITE);
                            buffer.addNamedModel(HAL.CollectionsAndResources, pathinfo);
                            buffer.removeNamedModel(r);
                            buffer.addNamedModel(r, m);
                            buffer.commit();
                            buffer.end();
                            System.out.println("Processed : "+r);
                        } catch (URISyntaxException ex) {
                            logger.error("1 - Problem Reading File : "+r);
                        } catch (IOException ex) {
                            logger.error("2 - Problem Reading File : "+r);
                        } catch (Exception ex) {
                            logger.error("3 - Problem Reading File : "+r+"\n"+ex.toString());
                        }
                    })
                ).get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.error(ex.toString());
            } finally {
                if (fjp != null) {
                    fjp.shutdown();
                }
            }
        } catch (IOException ex) {
            logger.error(ex.toString());
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
}
