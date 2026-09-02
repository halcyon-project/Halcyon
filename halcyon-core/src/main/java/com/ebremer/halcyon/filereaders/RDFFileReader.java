package com.ebremer.halcyon.filereaders;

import com.ebremer.halcyon.server.utils.PathMapper;
import com.ebremer.ns.LWS;
import com.ebremer.ns.PROVO;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;

/**
 *
 * @author Erich Bremer
 */
public class RDFFileReader extends AbstractFileReader {
    private Model m;    
    private static final Map<String, Lang> EXT_TO_LANG = new HashMap<>();
    private Optional<PathMapper> pathMapper;
    private Resource subject;

    static {
        EXT_TO_LANG.put("ttl", Lang.TURTLE);
        EXT_TO_LANG.put("nt", Lang.NT);
        EXT_TO_LANG.put("jsonld", Lang.JSONLD11);
        EXT_TO_LANG.put("rdf", Lang.RDFXML);
    }
    
    private static Lang getLangFromUri(URI uri) {
        String path = uri.getPath();
        if (path == null) return null;
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == path.length() - 1) {
            return null;
        }
        String ext = path.substring(dotIndex + 1).toLowerCase();
        return EXT_TO_LANG.get(ext);
    }

    public RDFFileReader(URI local, URI uri) {
        super(uri);
        m = ModelFactory.createDefaultModel();
        String baseURI = uri.toString();     
        m.createResource(baseURI)
                .addProperty(RDF.type, LWS.DataResource);
        Lang lang = getLangFromUri(uri);
        try (FileInputStream fis = new FileInputStream(new File(local))) {
            RDFParser.create()
                    .source(fis)
                    .base(uri.toString())
                    .lang(lang)
                    //errorHandler(ErrorHandlerFactory.errorHandlerStrict()) // Strict parsing
                    .parse(m);
        } catch (FileNotFoundException ex) {
            System.getLogger(RDFFileReader.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        } catch (IOException ex) {
            System.getLogger(RDFFileReader.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        subject = m.createResource(uri.toString());
    }
    
    public Model getBaseRDF(Resource r) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
        """
        construct {?s ?p ?o}
        where {            
            ?s ?p ?o
            bind(str(?p) as ?ns)
            filter (strstarts(?ns,?prov) || strstarts(?ns,?dc) || strstarts(?ns,?rdf))
        }
        """
        );
        pss.setIri("s", r.toString());
        pss.setLiteral("dc", DCTerms.NS);
        pss.setLiteral("prov", PROVO.NS);
        pss.setLiteral("rdf", RDF.uri);
        try (QueryExecution qexec = QueryExecutionFactory.create(pss.toString(), r.getModel())) {
            return qexec.execConstruct();
        }
    }
    
    public Resource getSubject() {
        return subject;
    }
        
    public RDFFileReader(URI uri, PathMapper pm) {
        super(uri);
        if (pm==null) {
            pathMapper = Optional.empty();
        } else {
            this.pathMapper = Optional.of(pm);
        }
        m = ModelFactory.createDefaultModel();
        String baseURI = uri.toString();     
        m.createResource(baseURI)
                .addProperty(RDF.type, LWS.DataResource);
        Lang lang = getLangFromUri(uri);
        URI src;
        if (pathMapper.isEmpty()) {
            src = uri;
        } else {
            PathMapper pmx = pathMapper.get();
            Optional<URI> x = pmx.http2file(uri);
            if (x.isPresent()) {
                src = x.get();
            } else {
                throw new Error("file does not exist : "+uri);
            }
        }
        try (FileInputStream fis = new FileInputStream(new File(src))) {
            RDFParser.create()
                    .source(fis)
                    .base(baseURI)
                    .lang(lang)
                    //errorHandler(ErrorHandlerFactory.errorHandlerStrict()) // Strict parsing
                    .parse(m);
        } catch (FileNotFoundException ex) {
            System.getLogger(RDFFileReader.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        } catch (IOException ex) {
            System.getLogger(RDFFileReader.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }

    @Override
    public Model getMeta() {
        return m;
    }

    @Override
    public Model getMeta(URI uri) {
        // The document IS RDF; its metadata is what it says about the resource
        // itself. Root-focused and namespace-filtered (rdf/dcterms/prov) via
        // getBaseRDF — enough to carry the document's own rdf:type (a Zephyr
        // stack saved into an LWS storage types itself zeph:Stack, which is
        // what the storage listings and viewer bindings key on) without
        // merging the entire content graph into the resource's metadata.
        // This overload is the one the LWS metadata scanner calls; it used to
        // throw UnsupportedOperationException, so RDF resources were never
        // typed at all.
        return getBaseRDF(m.createResource(uri.toString()));
    }

    @Override
    public String getFormat() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Set<String> getSupportedFormats() {
        Set<String> set = new HashSet<>();
        set.add("nt");
        set.add("ttl");
        set.add("jsonld");
        return set;
    }

    @Override
    public void close() {}
    
    public static void main(String[] args) {
        
        URI uri = URI.create("https://localhost:8888/ldp/utah/HnE/Stack2/stack.jsonld");
        
        /*
        File settingsFile = new File("D:\\projects\\Halcyon\\Halcyon\\settings.ttl");
        HalcyonSettings settings = HalcyonSettings.getSettings(settingsFile);
        PathMapper pathMapper = PathMapper.getPathMapper(settings);
        RDFFileReader r = new RDFFileReader(uri,pathMapper);
        RDFDataMgr.write(System.out, r.getMeta(), Lang.TURTLE);  
        */
        
        URI local = URI.create("file:///D:/HalcyonStorage/utah/HnE/Stack2/stack.jsonld");
        RDFFileReader r2 = new RDFFileReader(local,uri);
        RDFDataMgr.write(System.out, r2.getMeta(), Lang.TURTLE); 
    }
    
}
