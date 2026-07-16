package com.ebremer.halcyon.wicket;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.JsonLdVersion;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.serialization.RdfToJsonld;
import com.apicatalog.rdf.RdfDataset;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.server.utils.PathFinder;
import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.utils.ColorTools;
import com.ebremer.halcyon.utils.HFrame;
import com.ebremer.halcyon.utils.HalColors;
import com.ebremer.ns.HAL;
import com.ebremer.ns.PROVO;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.system.jsonld.JenaToTitanium;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SchemaDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class FeatureManager {

    private static final Logger logger = LoggerFactory.getLogger(FeatureManager.class);
    
    public static String getFeatures(HashSet<String> features, String urn) {
        Dataset ds = DataCore.getInstance().getDataset();
        Iterator<String> ii = features.iterator();
        Model wow = ModelFactory.createDefaultModel();
        ArrayList<RDFNode> createactions = new ArrayList<>();
        while (ii.hasNext()) {
            RDFNode node = wow.createResource(ii.next());
            createactions.add(node);
        }
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            select distinct ?roc
            where {
                values (?wasAssociatedWith) {?selected}
                graph ?roc {
                    ?fc prov:wasGeneratedBy [ a prov:Activity;
                                                 prov:used ?md5;
                                                 prov:wasAssociatedWith ?wasAssociatedWith
                                           ]
                }
                graph ?image {?image owl:sameAs ?md5}
            }
            """);
        pss.setNsPrefix("prov", PROVO.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("dct", DCTerms.NS);
        pss.setNsPrefix("rdfs", RDFS.getURI());
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setIri("image", urn);
        pss.setValues("selected", createactions);
        // H13: end() must be in a finally. Everything between begin and end can
        // throw — pss.toString() raises ARQException on a bad substitution, create()
        // raises QueryParseException, and materialise() pulls the WHOLE result set
        // into memory, so it can OOM. Any of those used to skip end() and strand a
        // READ transaction on this Wicket worker thread; the thread then fails
        // EVERY later begin() with "Currently in an active transaction" for the rest
        // of the process's life. See LwsServlet.service, which reaps exactly this.
        ResultSet results;
        ds.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds)) {
            logger.debug(pss.toString());
            results = qe.execSelect().materialise();   // materialise: safe to read after end()
        } finally {
            ds.end();
        }
        ArrayList<RDFNode> roc = new ArrayList<>();
        while (results.hasNext()) {
            QuerySolution qs = results.next();
            roc.add(qs.get("roc"));
        }
        
        pss = new ParameterizedSparqlString("""
            select distinct ?roc ?type
            where {
                values (?roc) {?selected}
                graph ?roc {?rocx hal:hasClassification ?type }
            }
            """);        
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("rdfs", RDFS.getURI());
        pss.setNsPrefix("rdf", RDF.uri);        
        pss.setValues("selected", roc);
        // H13: as above — guarded end() and a closed QueryExecution.
        ResultSet rs;
        ds.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds)) {
            logger.debug(pss.toString());
            rs = qe.execSelect().materialise();
        } finally {
            ds.end();
        }
        record ColorCode(String color, int code, String name) {}
        HashMap<Resource,ColorCode> types = new HashMap<>();
        HalColors cs = new HalColors();
        UserColorsAndClasses ucac = new UserColorsAndClasses();
        HashSet<Resource> rocs = new HashSet<>();
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            Resource key = qs.get("type").asResource();
            rocs.add(qs.get("roc").asResource());
            String color = ucac.getColor(key);
            String name = ucac.getName(key);
            // M7: Hex2RGBA now returns null for a hal:color that isn't a valid
            // hex colour (it used to throw straight out of this response). Treat
            // an unusable colour exactly like a missing one and fall back to the
            // auto-assigned palette rather than passing null downstream.
            String rgba = ColorTools.Hex2RGBA(color);
            if (rgba!=null) {
                types.put(key,new ColorCode(rgba,types.size()+1,name));
            } else if (!types.containsKey(key)) {
                types.put(key,new ColorCode(cs.removeFirst(),types.size()+1,"Unknown"));
            }
        }

        Model m = ModelFactory.createDefaultModel();
        int c = 0;
        Resource layer = m.createResource()
                    .addProperty(RDF.type, HAL.FeatureLayer)
                    .addLiteral(HAL.layerNum, c)
                    .addProperty(HAL.location, PathFinder.LocalPath2IIIFInfoURL(urn))
                    .addLiteral(HAL.opacity, 1)
                    .addProperty(HAL.colorscheme, m.createResource()
                        .addProperty(SchemaDO.name, "Default Color Scheme")
                        .addProperty(RDF.type, HAL.ColorScheme)
                        .addProperty(HAL.colorspectrum, 
                            m.createResource()
                                .addLiteral(HAL.color, "rgba(254, 251, 191, 255)")
                                .addLiteral(HAL.high, 150)
                                .addLiteral(HAL.low, 101)
                        )
                        .addProperty(HAL.colorspectrum, 
                            m.createResource()
                                .addLiteral(HAL.color, "rgba(44, 131, 186, 255)")
                                .addLiteral(HAL.high, 50)
                                .addLiteral(HAL.low, 0)
                        )
                        .addProperty(HAL.colorspectrum, 
                            m.createResource()
                                .addLiteral(HAL.color, "rgba(246, 173, 96, 255)")
                                .addLiteral(HAL.high, 200)
                                .addLiteral(HAL.low, 151)
                        )
                        .addProperty(HAL.colorspectrum, 
                            m.createResource()
                                .addLiteral(HAL.color, "rgba(171, 221, 164, 255)")
                                .addLiteral(HAL.high, 100)
                                .addLiteral(HAL.low, 51)
                        )
                        .addProperty(HAL.colorspectrum, 
                            m.createResource()
                                .addLiteral(HAL.color, "rgba(216, 63, 42, 255)")
                                .addLiteral(HAL.high, 255)
                                .addLiteral(HAL.low, 201)
                        )
                    .addProperty(HAL.colors, m.createResource()
                        .addLiteral(SchemaDO.name, "This is an image")
                        .addLiteral(HAL.classid, 1000)
                        .addLiteral(HAL.color, "rgba(255, 225, 255, 255)")
                        .addLiteral(HAL.color, "rgba(255, 225, 255, 255)")
                        .addLiteral(HAL.color, "rgba(255, 225, 255, 255)")
                        .addLiteral(HAL.color, "rgba(255, 225, 255, 255)")
                    )
            );
        Resource LayerSet = m.createResource().addProperty(RDF.type, HAL.LayerSet);
        LayerSet.addProperty(HAL.haslayer, layer);
        Resource COLORSCHEME = m.createResource();
        Iterator<Resource> rocx = rocs.iterator();        
        while (rocx.hasNext()) {
            c++;
            Resource rockey = rocx.next();
            Iterator<Resource> typex = types.keySet().iterator();
            while (typex.hasNext()) {
                Resource typekey = typex.next();                          
                    layer = m.createResource()
                        .addProperty(RDF.type, HAL.FeatureLayer)
                        .addLiteral(HAL.layerNum, c)
                        .addProperty(HAL.location, PathFinder.LocalPath2IIIFInfoURL(rockey.getURI()))
                        .addLiteral(SchemaDO.name, "Name Unknown")
                        .addLiteral(HAL.opacity, 0.5);
                    COLORSCHEME
                            .addProperty(SchemaDO.name, "Default Color Scheme")
                            .addProperty(RDF.type, HAL.ColorScheme)
                            .addProperty(HAL.colorspectrum, 
                                m.createResource()
                                    .addLiteral(HAL.color, "rgba(254, 251, 191, 255)")
                                    .addLiteral(HAL.high, 150)
                                    .addLiteral(HAL.low, 101)
                            )
                            .addProperty(HAL.colorspectrum, 
                                m.createResource()
                                    .addLiteral(HAL.color, "rgba(44, 131, 186, 255)")
                                    .addLiteral(HAL.high, 50)
                                    .addLiteral(HAL.low, 0)
                            )
                            .addProperty(HAL.colorspectrum, 
                                m.createResource()
                                    .addLiteral(HAL.color, "rgba(246, 173, 96, 255)")
                                    .addLiteral(HAL.high, 200)
                                    .addLiteral(HAL.low, 151)
                            )
                            .addProperty(HAL.colorspectrum, 
                                m.createResource()
                                    .addLiteral(HAL.color, "rgba(171, 221, 164, 255)")
                                    .addLiteral(HAL.high, 100)
                                    .addLiteral(HAL.low, 51)
                            )
                            .addProperty(HAL.colorspectrum, 
                                m.createResource()
                                    .addLiteral(HAL.color, "rgba(216, 63, 42, 255)")
                                    .addLiteral(HAL.high, 255)
                                    .addLiteral(HAL.low, 201)
                            );
                    layer.addProperty(HAL.colorscheme,COLORSCHEME);
                    LayerSet.addProperty(HAL.haslayer, layer);
                COLORSCHEME.addProperty(HAL.colors, m.createResource()
                        .addLiteral(SchemaDO.name, types.get(typekey).name())
                        .addLiteral(HAL.classid, types.get(typekey).code())
                        .addLiteral(HAL.color, types.get(typekey).color())
                );
            }
        }
        Dataset dss = DatasetFactory.createGeneral();
        dss.getDefaultModel().add(m);
        JsonArray ja;
        String hold = null;
        try {
            JsonLdOptions fromRdfOptions = new JsonLdOptions();
            fromRdfOptions.setUseNativeTypes(true);
            ja = JenaToTitanium.convert(dss.asDatasetGraph(), fromRdfOptions);
            JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JsonWriter out = writerFactory.createWriter(baos);
            JsonLdOptions options = new JsonLdOptions();
            options.setProcessingMode(JsonLdVersion.V1_1);
            options.setUseNativeTypes(true);
            JsonObject jo = JsonLd.compact(JsonDocument.of(ja), HFrame.getViewerContext()).options(options).get();
            jo = HFrame.frame(jo, options);
            out.writeObject(jo);
            hold = new String(baos.toByteArray());
        } catch (JsonLdError ex) {
            logger.error(ex.toString());
        }
      return HFrame.wow(hold);
    }
}
