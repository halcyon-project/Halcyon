package com.ebremer.halcyon.fea;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.JsonLdVersion;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.serialization.RdfToJsonld;
import com.apicatalog.rdf.RdfDataset;
import com.ebremer.halcyon.converters.BadMetaFile;
import com.ebremer.halcyon.converters.MetaManager;
import com.ebremer.ns.HAL;
import com.ebremer.ns.IIIF;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Iterator;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParserBuilder;
import org.apache.jena.riot.RIOT;
import org.apache.jena.riot.system.JenaTitanium;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class ROCrate {
    public static String metadata = "ro-crate-metadata.jsonld";
    public static String metadatattl = "ro-crate-metadata.ttl";
    private Model rc = null;
    private final String rootIRI = "https://"+UUID.randomUUID().toString()+".com";
    private Resource root = null;
    private ZipFile archive;
    private MetaManager mm;
    
    
    public ROCrate() throws BadMetaFile {
        mm = new MetaManager();
        rc = mm.getROCrate(rootIRI);
        root = rc.createResource(rootIRI);
        rc.add(root,RDF.type,HAL.HalcyonROCrate);
        init();
    }
    
    public ROCrate(MetaManager mm) throws BadMetaFile {
        rc = mm.getROCrate(rootIRI);
        root = rc.createResource(rootIRI);
        init();
    }
    
    public ROCrate(String source) throws BadMetaFile, FileNotFoundException, IOException {
        mm = null;
        File file = new File(source);
        Model m = ModelFactory.createDefaultModel();
        InputStream bis = new BufferedInputStream(new FileInputStream(file));   
        if (file.toString().endsWith(".gz")) {
            bis = new GZIPInputStream(bis); 
        }    
        System.out.println("BASE : "+rootIRI);
        RDFParserBuilder.create()
            .base(rootIRI)
            .set(RIOT.symTurtleOmitBase, false)
            .source(bis)
            .lang(Lang.TURTLE)
            .build().parse(m);
        //RDFDataMgr.write(System.out, m, Lang.TURTLE);
        rc = m;
        root = rc.createResource(rootIRI);
        //  disabling ROCrate validation during testing Validate();
        init();
        
    }

    private void init() {
        ZonedDateTime dateTime = ZonedDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        rc.add(rc.createStatement(root, SchemaDO.datePublished, dateTime.format(formatter)));
        Resource manifest = rc.createResource(rootIRI+"/"+ROCrate.metadata);
        //rc.add(rc.createStatement(manifest, RDF.type, SchemaDO.CreativeWork));
        //rc.add(rc.createStatement(manifest, rc.createProperty("http://purl.org/dc/terms/conformsTo"), rc.createResource("https://w3id.org/ro/crate/1.1")));
        rc.add(manifest, RDF.type, SchemaDO.CreativeWork);
        rc.add(manifest, rc.createProperty("http://purl.org/dc/terms/conformsTo"), rc.createResource("https://w3id.org/ro/crate/1.1"));
    }
    
    public final boolean Validate() throws BadMetaFile {
        //Graph shapesGraph = RDFDataMgr.loadGraph("shapes.ttl");
        Shapes shapes = Shapes.parse(SHACL.getGraph11());
        System.out.println("Checking file structure...");
        ValidationReport report = ShaclValidator.get().validate(shapes, rc.getGraph());
        ShLib.printReport(report);
        boolean valid = report.conforms();
        if (!valid) {
            throw new BadMetaFile("Bad ROCrate");
        }
        return true;
    }
    
    public ROCrate(File f) throws IOException {
        archive = new ZipFile(f);
    }
    
    public void close() throws IOException {
        archive.close();
    }
    
    public boolean isRO() {
        return archive.getEntries(ROCrate.metadata).iterator().hasNext();
    }
    
    public Model getManifest(String base) {
        Iterable<ZipArchiveEntry> e = archive.getEntries(ROCrate.metadata);
        Iterator<ZipArchiveEntry> i = e.iterator();
        Model m = null;
        if (i.hasNext()) {
            ZipArchiveEntry roc = i.next();
            m = ModelFactory.createDefaultModel();
            InputStream inputStream;
            try {
                inputStream = archive.getInputStream(roc);
                RDFParserBuilder.create()
                        .base(base)
                        .source(inputStream)
                        .lang(RDFLanguages.JSONLD10).build()
                        .parse(m);
            } catch (IOException ex) {
                Logger.getLogger(ROCrate.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return m;
    }
       
    public String getRootIRI() {
        return rootIRI;
    }    
    
    public Resource getRoot() {
        return root;
    }
      
    public Model getModel() {
        return rc;
    }

    public void addFile(String file, String name, String description) {
        Resource c = rc.createResource(rootIRI+file);
        rc.add(root,SchemaDO.hasPart,c);
        rc.add(c, SchemaDO.name, name);
        rc.add(c, SchemaDO.description, description);
        rc.add(root, SchemaDO.creator, c);
    }
    
    public void addFolder(String folder, String name, String description) {
        Resource c = rc.createResource(rootIRI+folder);
        rc.add(root,SchemaDO.hasPart,c);
        rc.add(c, SchemaDO.name, name);
        rc.add(c, SchemaDO.description, description);
        rc.add(root, SchemaDO.creator, c);
    }
        
    public void AddCreator(String orcid, String name, String email) {
        Resource c = rc.createResource(orcid);
        rc.add(c, SchemaDO.name, name);
        rc.add(c, SchemaDO.email, email);
        rc.add(root, SchemaDO.creator, c);
    }
    
    public void SetdatePublished() {
        ZonedDateTime dateTime = ZonedDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        rc.add(rc.createLiteralStatement(root, SchemaDO.datePublished,dateTime.format(formatter)));
    }
    /*
    public String toStringOLD() {
        JsonLDWriteContext jenaCtx = new JsonLDWriteContext();
        JsonLdOptions opts = new JsonLdOptions(null);
        opts.setCompactArrays(true);
        System.out.println("BASE IRI = "+rootIRI);
        opts.setBase(rootIRI);
        opts.useNamespaces = true;
        opts.setUseNativeTypes(Boolean.TRUE);
        DocumentLoader dl = new DocumentLoader();
        dl.loadDocument("https://w3id.org/ro/crate/1.1/context");
        dl.loadDocument("https://www.w3.org/ns/csvw");
        dl.loadDocument("https://schema.org/");
        opts.setDocumentLoader(dl);
        jenaCtx.setOptions(opts);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            RDFWriter.create()
                    .base(null)
                    .set(RIOT.symTurtleOmitBase, true)
                    .source(rc)
                    .format(RDFFormat.JSONLD11_PRETTY)
                    .context(jenaCtx)
                    .output(out);
            out.flush();
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    */
    @Override
    public String toString() {
        try {
            Dataset ds = DatasetFactory.createGeneral();
            ds.getDefaultModel().add(rc);
            RdfDataset rds = JenaTitanium.convert(ds.asDatasetGraph());
            RdfToJsonld rtj = RdfToJsonld.with(rds);
            JsonArray ja = rtj.build();
            JsonLdOptions options = new JsonLdOptions();
            options.setBase(new URI(rootIRI+"/"));
            options.setUseNativeTypes(true);
            options.setProcessingMode(JsonLdVersion.V1_1);
            options.setCompactToRelative(true);
            JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JsonWriter out = writerFactory.createWriter(baos);
            Document contextDocument = JsonDocument.of(new ByteArrayInputStream(IIIF.CONTEXT.getBytes()));
            JsonObject jo = JsonLd.compact(JsonDocument.of(ja), contextDocument).options(options).get();
            out.writeObject(jo);
            String hold = new String(baos.toByteArray());
            hold = hold.replaceAll(rootIRI+"/", "./");
            return hold;
        } catch (JsonLdError ex) {
            Logger.getLogger(ROCrate.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(ROCrate.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(ROCrate.class.getName()).log(Level.SEVERE, null, ex);
        } catch (URISyntaxException ex) {
            Logger.getLogger(ROCrate.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
