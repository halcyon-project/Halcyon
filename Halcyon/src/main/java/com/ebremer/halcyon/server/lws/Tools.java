package com.ebremer.halcyon.server.lws;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.api.FramingApi;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.lang.Keywords;
import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.lib.OperatingSystemInfo;
import com.ebremer.halcyon.server.lws.ShapeSparqlTemplates;
import com.ebremer.halcyon.server.utils.PathMapper;
import com.ebremer.halcyon.utils.HalJsonLD;
import com.ebremer.ns.HAL;
import com.ebremer.ns.IANA;
import com.ebremer.ns.LWS;
import com.ebremer.ns.SNO;
import com.ebremer.ns.STAT;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.jsonld.JenaToTitanium;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class Tools {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(Tools.class);
    public record Prefer(String _return, List<Resource> include, List<Resource> omit, int _wait, String handling, List<Resource> shacl) {};
    
    public static Model getLDPMeta(Resource r, File file) {
        r
            .addProperty(RDF.type, LWS.MetadataResource)
            .addProperty(RDF.type, IANA.JSON)
            .addProperty(RDF.type, HAL.Annotation)
            .addLiteral(STAT.mtime, file.lastModified()/1000)
            .addLiteral(STAT.size, file.length());
        if (file.exists()) {
            BasicFileAttributes attrs;
            try {
                attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                Instant instant = attrs.lastModifiedTime().toInstant();
                String isoDateTime = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC")).format(instant);
                r.addLiteral(DCTerms.modified, ResourceFactory.createTypedLiteral(isoDateTime, XSDDatatype.XSDdateTime));
            } catch (IOException ex) {
                Logger.getLogger(Tools.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return r.getModel();
    }
    
    public static void Save(HttpServletRequest request, HttpServletResponse response) {
        String uri = request.getRequestURL().toString();
        String data = Utils.getBody(request);
        try (JsonReader jsonReader = Json.createReader(new StringReader(data))) {
            JsonArray ja = jsonReader.readArray();
            JsonObject jo = ja.getJsonObject(ja.size()-1);            
            if (jo.containsKey("image")) {
                String name = jo.getString("image");     
                Optional<URI> x = PathMapper.getPathMapper().http2file(uri);
                if (x.isPresent()) {
                    URI dest = x.get();
                    File file;
                    if (OperatingSystemInfo.ifWindows()) {
                        file = new File(dest.getPath().substring(1));
                    } else {
                        file = new File(dest.getPath());
                    }
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(data.getBytes());
                    } catch (FileNotFoundException ex) {
                        Logger.getLogger(Tools.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IOException ex) {
                        Logger.getLogger(Tools.class.getName()).log(Level.SEVERE, null, ex);
                    }                    
                    Model m = ModelFactory.createDefaultModel();  
                    Resource anno = m.createResource(uri);
                    getLDPMeta(anno,file);
                    m.createResource(name).addProperty( HAL.annotation, anno );
                    Dataset ds = DataCore.getInstance().getDataset();
                    ds.begin(ReadWrite.WRITE);
                    ds.getNamedModel(HAL.CollectionsAndResources).add(m);
                    ds.commit();
                    ds.end();
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType("text/html;charset=UTF-8");
                    try (PrintWriter out = response.getWriter()) {
                        out.println("<html>");
                        out.println("<head>");
                        out.println("<title>Status OK</title>");
                        out.println("</head>");
                        out.println("<body>");
                        out.println("<h1>All is well!</h1>");
                        out.println("</body>");
                        out.println("</html>");
                    } catch (IOException ex) {
                        Logger.getLogger(Tools.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        }
    }
    
    private static List<Resource> convertStringToResourceList(String uriString) {
        String[] uris = uriString.replace("\"", "").split("\\s+");
        List<Resource> resourceList = new ArrayList<>();
        Model model = ModelFactory.createDefaultModel();
        for (String uri : uris) {
            Resource resource = model.createResource(uri);
            resourceList.add(resource);
        }
        return resourceList;
    }
    
    public static Optional<Prefer> getPreferHeader(HttpServletRequest request) {    
        Enumeration<String> header = request.getHeaders("Prefer");   
        final HashMap<String,String> map = new HashMap<>();
        if (header!=null) {
            header.asIterator().forEachRemaining(p->{
                String[] two = p.split(";");
                for (String pair: two) {
                    String[] param = pair.split("=");
                    if (param.length==2) {
                        String key = param[0].trim().toLowerCase();
                        if (!map.containsKey(key)) {
                            map.put(key, param[1].trim());
                        } else {} //duplicate keys shouldn't occur
                    } else {} // should always be A=B or A=B;C=D;...
                }
            });
        } else {
            return Optional.empty();
        }
        String _return = map.containsKey("return")?map.get("return"):null;
        List<Resource> include = map.containsKey("include")?convertStringToResourceList(map.get("include")):new ArrayList<>();
        List<Resource> omit = map.containsKey("omit")?convertStringToResourceList(map.get("omit")):new ArrayList<>();
        int _wait = map.containsKey("wait")?Integer.parseInt(map.get("wait")):0;
        String handling = map.containsKey("handling")?map.get("handling"):null;
        List<Resource> shacl = map.containsKey("shacl")?convertStringToResourceList(map.get("shacl")):new ArrayList<>();
        return Optional.of(new Prefer(_return, include, omit, _wait, handling, shacl));
    }
    
    public static List<String> getAllRDFTypes(String r) {
        logger.info("getAllRDFTypes ----> {}",r);
        Resource rr = ResourceFactory.createResource(r);
        ParameterizedSparqlString pss = new ParameterizedSparqlString("select distinct ?type where { ?s a ?type }");
        pss.setIri("s", rr.getURI());
        final List<String> types = new ArrayList<>();
        Dataset ds = DataCore.getInstance().getDataset();
        ds.begin(ReadWrite.READ);
        QueryExecutionFactory.create(pss.toString(),ds.getUnionModel())
                .execSelect()
                .forEachRemaining(qs->{
                    types.add(qs.get("type").toString());
                });
        ds.end();
        return types;
    }  
    
    public static String getHalcyonType(String r) {
        String wow = null;
        for (String t:getAllRDFTypes(r)) {
            if (t.startsWith(HAL.NS)) {
                wow = t;
            }            
        }
        return wow;
    }
    
    public static Resource getRDF(Optional<Tools.Prefer> prefer, String uri) {
        ParameterizedSparqlString pss = null;
        if (prefer.isPresent()) {
            String shape = (prefer.get().shacl().isEmpty())?null:prefer.get().shacl().get(0).getURI();
            pss = ShapeSparqlTemplates.getInstance().getPSS(shape);            
        }
        if (pss==null) {
            if (prefer.isEmpty()) {
                pss = new ParameterizedSparqlString("construct { ?s ?p ?o } where { ?s ?p ?o }");
            } else {
                pss = new ParameterizedSparqlString(
                    (prefer.get().include().isEmpty())?
                        "construct { ?s ?p ?o } where { ?s ?p ?o }":
                        "construct { ?s ?p ?o } where { ?s ?p ?o values (?p) { ?includes }}");                       
            }       
        }
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("s", uri);
        if (prefer.isPresent()) {
            pss.setValues("includes", prefer.get().include());
        }
        logger.info("getRDF ----> {}",pss.toString());
        System.out.println(pss.toString());
        Dataset ds = DataCore.getInstance().getDataset();
        ds.begin(ReadWrite.READ);
        Model k = QueryExecutionFactory.create(pss.toString(),ds.getUnionModel()).execConstruct();
        ds.end();
        RDFDataMgr.write(System.out, k, Lang.TURTLE);
        return k.createResource(uri);
    }
    
    public static void Resource2JSONLD(Optional<Tools.Prefer> prefer, Resource ha, OutputStream xout) {
        String shacl = null;
        if (prefer.isPresent()) {
            shacl = (prefer.get().shacl().size()==1)?prefer.get().shacl().get(0).getURI():null;
        }
        switch (shacl) {
            case "https://halcyon.is/ns/AnnotationClassListShape":
                AnnotationClasses2JSONLD(prefer, ha, xout);
                break;
            case "https://halcyon.is/ns/AnnotationsShape":
                Annotations2JSONLD(ha, xout);
                break;
            case null:
            default:
                RDFDataMgr.write(xout, ha.getModel(), Lang.JSONLD);
        }
    }

    public static void AnnotationClasses2JSONLD(Optional<Tools.Prefer> prefer, Resource ha, OutputStream xout) {
        System.out.println("AnnotationClasses2JSONLD =====> "+getHalcyonType(ha.getURI()));
        try {
            Model m = ha.getModel();
            m.setNsPrefix("hal", HAL.NS);
            m.setNsPrefix("annotation", "hal:annotation");
            m.setNsPrefix("sno", SNO.NS);
            Dataset dsx = DatasetFactory.create(m);
            DatasetGraph dsg = dsx.asDatasetGraph();
            JsonObjectBuilder cxt = Json.createObjectBuilder();
            dsg.prefixes().forEach((k, v) -> {
                if ( ! k.isEmpty() )
                    cxt.add(k, v);
            });
            cxt.add("hasAnnotationClass","hal:hasAnnotationClass");
            cxt.add("AnnotationClassList","hal:AnnotationClassList");
            cxt.add("AnnotationClass","hal:AnnotationClass");
            cxt.add("name","sdo:name");
            cxt.add("color","hal:color");
            cxt.add("hasClass", Json.createObjectBuilder()
                    .add(Keywords.ID, "hal:hasClass")
                    .add(Keywords.TYPE, Keywords.ID)
            );
            JsonLdOptions options = new JsonLdOptions();
            options.setOrdered(false);
            options.setUseNativeTypes(true);
            options.setOmitGraph(true);
            JsonArray array = JenaToTitanium.convert(dsg, options);
            JsonObject frame = Json.createObjectBuilder()
                    .add(Keywords.CONTEXT, cxt)
                    .add(Keywords.EMBED, Keywords.ALWAYS)
                    .add(Keywords.OMIT_DEFAULT, true)
                    .add(Keywords.REQUIRE_ALL, false)
                    .add(Keywords.TYPE, HAL.AnnotationClassList.getURI())
                    .build();
            FramingApi api = JsonLd.frame(JsonDocument.of(array), JsonDocument.of(frame));
            JsonStructure x = api.get();
            JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
            JsonWriter out = writerFactory.createWriter(xout);
            out.write(x);
        } catch (JsonLdError ex) {
            Logger.getLogger(HalJsonLD.class.getName()).log(Level.SEVERE, null, ex);
        }
    }      
    
    public static void Annotations2JSONLD(Resource ha, OutputStream xout) {       
        try {
            Model m = ha.getModel();
            RDFDataMgr.write(System.out, m, Lang.TURTLE);
            m.setNsPrefix("hal", HAL.NS);
            m.setNsPrefix("annotation", "hal:annotation");
            Dataset dsx = DatasetFactory.create(m);
            DatasetGraph dsg = dsx.asDatasetGraph();
            JsonObjectBuilder cxt = Json.createObjectBuilder();
            dsg.prefixes().forEach((k, v) -> {
                if ( ! k.isEmpty() )
                    cxt.add(k, v);
            });
            cxt.add("annotation", Json.createObjectBuilder()
                    .add(Keywords.ID, "hal:annotation")
                    .add(Keywords.CONTAINER, Keywords.SET)
                    .add(Keywords.TYPE, Keywords.ID)
            );
            JsonLdOptions options = new JsonLdOptions();
            options.setOrdered(false);
            options.setUseNativeTypes(true);
            options.setOmitGraph(true);
            JsonArray array = JenaToTitanium.convert(dsg, options);
            JsonObject frame = Json.createObjectBuilder()
                    .add(Keywords.CONTEXT, cxt)
                    .add(Keywords.EMBED, Keywords.ALWAYS)
                    .add(Keywords.OMIT_DEFAULT, true)
                    .add(Keywords.REQUIRE_ALL, false)
                    .add("annotation", Json.createObjectBuilder())
                    .build();
            FramingApi api = JsonLd.frame(JsonDocument.of(array), JsonDocument.of(frame));
            JsonStructure x = api.get();
            JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
            JsonWriter out = writerFactory.createWriter(xout);
            out.write(x);
        } catch (JsonLdError ex) {
            Logger.getLogger(HalJsonLD.class.getName()).log(Level.SEVERE, null, ex);
        }
    }    
}
