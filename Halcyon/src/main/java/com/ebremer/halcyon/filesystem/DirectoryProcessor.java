package com.ebremer.halcyon.filesystem;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.filereaders.FileReader;
import com.ebremer.halcyon.filereaders.FileReaderFactory;
import com.ebremer.halcyon.filereaders.FileReaderFactoryProvider;
import com.ebremer.halcyon.filereaders.ImageReader;
import com.ebremer.halcyon.filereaders.RDFFileReader;
import com.ebremer.halcyon.lib.FileUtils;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.server.utils.PathMapper;
import com.ebremer.halcyon.utils.HURI;
import com.ebremer.ns.HAL;
import com.ebremer.ns.LWS;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class DirectoryProcessor {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(DirectoryProcessor.class);
    public final int cores;
    private final Dataset buffer;
    private record FileMeta(long fileLastModified) {};
    private final ConcurrentHashMap<Resource, FileMeta> list;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Integer filemetaversion = 0;
    
    public DirectoryProcessor(Dataset buffer, int cores) {
        this.buffer = buffer;
        this.list = GetExisting();
        this.cores = cores;
        list.forEach((k,v)->{
            IO.println("EXISTS : "+k);
        });
    }
    
    public Model PathInfo(URI childuri) {
        Model m = ModelFactory.createDefaultModel();
        List<String> rootContainers = HalcyonSettings.getSettings().getRootContainers();
        URI npath = childuri;
        while (!isRootContainer(rootContainers, npath)) {
            URI xparent = HURI.getParent(npath);
            // M8: stop instead of spinning a core forever. HURI.getParent walks
            // up by dropping the last path segment, but it can never climb above
            // an empty path: once the path degrades to "" it returns "" for ever
            // (""/"/" split to <2 parts, so its loop emits nothing). So a child
            // that sits under NO configured root container -- including the case
            // where the only mismatch is a trailing slash, which isRootContainer
            // now tolerates -- used to loop forever on the ingestion path.
            if (xparent == null || xparent.equals(npath)
                    || xparent.getPath() == null || xparent.getPath().isEmpty()) {
                logger.warn("PathInfo: {} is not under any root container {} — stopping walk", childuri, rootContainers);
                break;
            }
            Resource parent = m.createResource(xparent.toString());
            Resource child = m.createResource(npath.toString());
            m.add(parent, LWS.contains, child);
            m.add(child, LWS.partOf, parent);
            m.add(parent, DCTerms.title, (new File(xparent.getPath()).getName()));
            m.add(parent, RDF.type, LWS.Container);
            npath = xparent;
        }
        return m;
    }

    /**
     * True when {@code uri} IS one of the configured root containers (M8).
     * <p>
     * Compares with any trailing slashes stripped from both sides: the walk
     * produces parents like {@code /lws/} while a root may be configured as
     * {@code /lws} (or vice versa), and the old exact {@code List.contains}
     * missed on that alone — so the walk sailed past its own root and reduced
     * the path to {@code ""}, never terminating.
     */
    static boolean isRootContainer(List<String> rootContainers, URI uri) {
        String path = stripTrailingSlashes(uri.getPath());
        for (String root : rootContainers) {
            if (stripTrailingSlashes(root).equals(path)) {
                return true;
            }
        }
        return false;
    }

    /** Drop trailing '/' (keeping a lone "/" intact); null becomes "". */
    static String stripTrailingSlashes(String path) {
        if (path == null) {
            return "";
        }
        String p = path;
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    public void Traverse(Path src) {
        try (ForkJoinPool fjp = new ForkJoinPool(cores)) {
            fjp.submit(()->{
                try {
                    Files.walk(src)
                        .filter(f->f.toFile().isFile())
                      //  .parallel()                  
                        .filter(fx->{
                            logger.trace("Processing this {}", fx);
                            Optional<URI> rrz = PathMapper.getPathMapper().file2http(fx.toUri());
                            if (rrz.isPresent()) {
                                logger.trace("Has PathMapper {}", fx);
                                Resource target = ResourceFactory.createResource(rrz.get().toString());
                                if (list.containsKey(target)) {
                                    return fx.toFile().lastModified() != list.get(target).fileLastModified();
                                } else {
                                    return true;
                                }
                            }
                            logger.trace("Mapping not found for {}", fx.toString());
                            return false;
                        })                        
                        .filter(fff->FileReaderFactoryProvider.hasReaderFor(fff))
                        .forEach(fx -> {
                            Optional<URI> rrz = PathMapper.getPathMapper().file2http(fx.toUri());
                            URI httpuri;
                            if (rrz.isPresent()) {
                                httpuri = rrz.get();
                            } else {
                                throw new Error("ACK!!!!");
                            }
                            logger.info("Processing ---> "+fx+"  "+httpuri.toString());
                            Model m = ModelFactory.createDefaultModel();
                            Resource r = m.createResource(httpuri.toString());
                            r.addProperty(OWL.sameAs, m.createResource(HURI.of(fx).toString()));
                           // File file = fx.toFile();
                            FileReaderFactory frf = FileReaderFactoryProvider.getReaderForFormat(r);
                            logger.info("Reader {}", frf);
                            try (FileReader fr = frf.create(fx.toUri(), httpuri)){
                                //logger.info("A");
                                Model xxx;                                
                                
                                //if (fr instanceof RDFFileReader rdf) {
                                  //  xxx = fr.getMeta();
                                //} else {
                                    xxx = fr.getMeta();
                                //}
                                /*
                                    RDFFileReaderFactory rdff = (RDFFileReaderFactory) FileReaderFactoryProvider.getReaderForFormat(r);
                                    RDFFileReader za = (RDFFileReader) rdff.create(httpuri, PathMapper.getPathMapper());
                                    xxx = za.getMeta();
                                } else {
                                    xxx = fr.getMeta();
                                }*/
                                //Model xxx = fr.getMeta();
                                //logger.info("B");
                                m.add(xxx);
                                //logger.info("C");
                                if (fr instanceof ImageReader) {
                                    //logger.info("D");
                                    r.addProperty(SchemaDO.fileFormat, FileUtils.getExtension(fr.getFormat()));
                                    //logger.info("E");
                                }
                                //logger.info("F");
                                r.addLiteral(HAL.validFile, true);
                                //logger.info("G");
                            } catch (Exception ex) {
                                logger.info("WHAT?!?! {} {}", fx, ex.getMessage());
                                r.addLiteral(HAL.validFile, false);
                                r.addLiteral(HAL.filemetaversion, m.createTypedLiteral(filemetaversion, XSD.integer.getURI()));
                            }
                            Model pathinfo;
                            ZonedDateTime dateTime = ZonedDateTime.now();
                            dateTime.format(formatter);
                            Literal dateLiteral = m.createTypedLiteral(dateTime.format(formatter), XSDDatatype.XSDdateTime);
                            r.addProperty(DCTerms.dateAccepted, dateLiteral);
                            r.addProperty(DCTerms.modified, dateLiteral);
                            r.addProperty(DCTerms.title, r.getLocalName());
                            r.addProperty(HAL.halcyonVersion, HalcyonSettings.VERSION);
                            //long now = System.nanoTime();
                            /*
                            Hashes hashes;                                    
                            try {
                                hashes = HashTools.calculateHashes(file);
                                r.addProperty(LOC.md5,hashes.MD5());
                                r.addProperty(OWL.sameAs, m.createResource("urn:md5:"+hashes.MD5()));
                                r.addProperty(LOC.sha256, hashes.SHA256());
                                r.addProperty(OWL.sameAs, m.createResource("urn:sha256:"+hashes.SHA256()));
                            } catch (NoSuchAlgorithmException ex) {
                                logger.error(ex.toString());
                            } catch (FileNotFoundException ex) {
                                logger.error(ex.toString());
                            } catch (IOException ex) {
                                logger.error(ex.toString());
                            }*/
                            //System.out.println("Time = "+((System.nanoTime()-now)/1000000000d));
                            pathinfo = PathInfo(httpuri);
                            //System.out.println("============== PATHINFO =====================");
                            //pathinfo.write(System.out, "TTL");
                            //System.out.println("============== DATA =====================");
                            //m.write(System.out, "TTL");
                            //System.out.println("RESOURCE "+r);
                            logger.trace("opening database for write");
                            // H13: guarded WRITE. This runs on ForkJoinPool workers
                            // during ingest — a strand both kills the worker and
                            // wedges every writer in the process.
                            buffer.begin(ReadWrite.WRITE);
                            try {
                                buffer.addNamedModel(HAL.CollectionsAndResources, pathinfo);
                                buffer.removeNamedModel(r);
                                buffer.addNamedModel(r, m);
                                logger.trace("commit data");
                                buffer.commit();
                            } catch (RuntimeException ex) {
                                buffer.abort();
                                throw ex;
                            } finally {
                                buffer.end();
                            }
                            logger.info("Processed : {} {}", r, m.size());
                        });
                } catch (IOException ex) {
                    Logger.getLogger(DirectoryProcessor.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            ).get();
        } catch (InterruptedException | ExecutionException ex) {
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
                        ?g :filemetaversion ?filemetaversion;
                            :validFile ?valid;
                            :fileLastModified ?fileLastModified
                        FILTER (?filemetaversion = ?curr)
                      }
                    }
                    """
            );
            pss.setNsPrefix("", HAL.NS);
            pss.setLiteral("curr", filemetaversion);
            // H13: close the QueryExecution (end() below is already in a finally).
            ResultSet results;
            try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds)) {
                results = qe.execSelect().materialise();
            }
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
