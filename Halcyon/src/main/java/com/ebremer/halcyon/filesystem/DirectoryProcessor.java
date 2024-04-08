package com.ebremer.halcyon.filesystem;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.filereaders.FileReader;
import com.ebremer.halcyon.filereaders.FileReaderFactory;
import com.ebremer.halcyon.filereaders.FileReaderFactoryProvider;
import com.ebremer.halcyon.filereaders.ImageReader;
import com.ebremer.halcyon.lib.FileUtils;
import com.ebremer.halcyon.lib.URITools;
import com.ebremer.halcyon.raptor.HilbertSpecial;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.utils.HashTools;
import com.ebremer.halcyon.utils.HashTools.Hashes;
import com.ebremer.ns.HAL;
import com.ebremer.ns.LOC;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
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
    private record FileMeta(long fileLastModified) {};
    private final ConcurrentHashMap<Resource, FileMeta> list;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(HilbertSpecial.class);
    private static final Integer filemetaversion = 0;
    
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
                    .filter(fx->{
                        Resource target = ResourceFactory.createResource(URITools.fix(fx.toUri().toString()));
                        if (list.containsKey(target)) {
                            return fx.toFile().lastModified() != list.get(target).fileLastModified();
                        } else {
                            return true;
                        }  
                    })
                    .map(path->ResourceFactory.createResource(URITools.fix(path.toUri().toString())))
                    .filter(fff->FileReaderFactoryProvider.hasReaderFor(fff))
                    .forEach(r -> {
                        System.out.println("Processing ---> "+r);
                        URI uri;                        
                        try {                         
                            uri = new URI(r.getURI());
                            Model m = ModelFactory.createDefaultModel();
                            File file = new File(uri);
                            FileReaderFactory frf = FileReaderFactoryProvider.getReaderForFormat(r);                            
                            try (FileReader fr = frf.create(uri)){
                                m.add(fr.getMeta());
                                if (fr instanceof ImageReader) {
                                    m.add(r, SchemaDO.fileFormat, FileUtils.getExtension(fr.getFormat()));
                                }                            
                                m.addLiteral(r, HAL.validFile, true);
                            } catch (Exception ex) {                                
                                m.addLiteral(r, HAL.validFile, false);
                                m.addLiteral(r, HAL.filemetaversion, filemetaversion);
                            }
                            Model pathinfo;
                            try {
                                ZonedDateTime dateTime = ZonedDateTime.now();
                                dateTime.format(formatter);
                                Literal dateLiteral = m.createTypedLiteral(dateTime.format(formatter), XSDDatatype.XSDdateTime);
                                m.add(r, SchemaDO.datePosted, dateLiteral);
                                m.add(r, SchemaDO.name, r.getLocalName());
                                m.addLiteral(r,SchemaDO.contentSize,file.length());
                                m.add(r,SchemaDO.instrument, HalcyonSettings.HALCYONAGENT);
                                m.add(r,HAL.halcyonVersion, HalcyonSettings.VERSION);
                                m.addLiteral(r, HAL.fileLastModified, file.lastModified());                                
                                long now = System.nanoTime();
                                Hashes hashes;
                                try {
                                    hashes = HashTools.calculateHashes(file);
                                    m.add(r,LOC.md5,hashes.MD5());
                                    m.add(r, OWL.sameAs, m.createResource("urn:md5:"+hashes.MD5()));
                                    m.add(r, LOC.sha256, hashes.SHA256());
                                    m.add(r, OWL.sameAs, m.createResource("urn:sha256:"+hashes.SHA256()));
                                } catch (NoSuchAlgorithmException ex) {
                                    Logger.getLogger(DirectoryProcessor.class.getName()).log(Level.SEVERE, null, ex);
                                } catch (FileNotFoundException ex) {
                                    Logger.getLogger(DirectoryProcessor.class.getName()).log(Level.SEVERE, null, ex);
                                }
                                System.out.println("Time = "+((System.nanoTime()-now)/1000000000d));                                
                                pathinfo = PathInfo(file.getCanonicalPath());
                                buffer.begin(ReadWrite.WRITE);
                                buffer.addNamedModel(HAL.CollectionsAndResources, pathinfo);
                                buffer.removeNamedModel(r);
                                //System.out.println("=================================================================");
                                //RDFDataMgr.write(System.out, m, Lang.TURTLE);
                                //System.out.println("=================================================================");
                                buffer.addNamedModel(r, m);
                                buffer.commit();
                                buffer.end();
                            } catch (IOException ex) {
                                Logger.getLogger(DirectoryProcessor.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        } catch (URISyntaxException ex) {
                            Logger.getLogger(DirectoryProcessor.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        System.out.println("Processed : "+r);
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
    
    private ConcurrentHashMap<Resource, FileMeta> GetExisting() {
        ConcurrentHashMap<Resource, FileMeta> cur = new ConcurrentHashMap<>();
        DataCore dc = DataCore.getInstance();
        Dataset ds = dc.getDataset();
        try {
            ds.begin(ReadWrite.READ);
            ParameterizedSparqlString pss = new ParameterizedSparqlString(
                    """
                    SELECT DISTINCT ?g ?fileLastModified
                    WHERE {
                      GRAPH ?g {
                        ?g  :filemetaversion ?filemetaversion;
                            :validFile ?valid;
                            :fileLastModified ?fileLastModified
                        FILTER (?filemetaversion = ?curr)
                      }
                    }
                    """
            );
            pss.setNsPrefix("", HAL.NS);
            pss.setLiteral("curr", filemetaversion);
            QueryExecution qe = QueryExecutionFactory.create(pss.toString(),ds);
            ResultSet results = qe.execSelect().materialise();
            while (results.hasNext()) {
                QuerySolution sol = results.next();
                if (!cur.containsKey(sol.get("g").asResource())) {
                    cur.put(sol.get("g").asResource(), new FileMeta(sol.get("fileLastModified").asLiteral().getLong()));
                }
            }
        } finally {
            ds.end();
        }
        return cur;
    }
}
