package com.ebremer.halcyon.wicket;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.JsonLdVersion;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.serialization.RdfToJsonld;
import com.apicatalog.rdf.RdfDataset;
import com.ebremer.halcyon.lib.HalcyonSettings;
import com.ebremer.halcyon.lib.PathFinder;
import com.ebremer.halcyon.datum.DataCore;
import com.ebremer.halcyon.utils.ColorTools;
import com.ebremer.halcyon.utils.HFrame;
import com.ebremer.halcyon.utils.HalColors;
import com.ebremer.ns.HAL;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.system.JenaTitanium;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class FeatureManager {

    public static String getFeatures(HashSet<String> features, String urn) {
        Dataset ds = DataCore.getInstance().getDataset();
        Iterator<String> ii = features.iterator();
        Model wow = ModelFactory.createDefaultModel();
        ArrayList<RDFNode> createactions = new ArrayList<>();
        while (ii.hasNext()) {
            RDFNode node = wow.createResource(ii.next());
            createactions.add(node);
        }
        String host = HalcyonSettings.getSettings().getProxyHostName();
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            select distinct ?roc
            where {
                values (?ca) {?selected}
                graph ?roc {?ca so:object ?md5}
                graph ?image {?image owl:sameAs ?md5}
            }
            """);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("rdfs", RDFS.getURI());
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setIri("image", urn);
        pss.setValues("selected", createactions);
        ds.begin(ReadWrite.READ);
        ResultSet results = QueryExecutionFactory.create(pss.toString(), ds).execSelect().materialise();
        ds.end();
        ArrayList<RDFNode> roc = new ArrayList<>();
        while (results.hasNext()) {
            QuerySolution qs = results.next();
            roc.add(qs.get("roc"));
        }
        pss = new ParameterizedSparqlString("""
            select distinct ?roc ?type ?label ?value
            where {
                values (?roc) {?selected}
                graph ?roc {?roc hal:hasFeature ?feature .
                ?feature a ?type; rdfs:label ?label; rdf:value ?value
            }}
            """);        
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("rdfs", RDFS.getURI());
        pss.setNsPrefix("rdf", RDF.uri);        
        pss.setValues("selected", roc);
        ds.begin(ReadWrite.READ);
        ResultSet rs = QueryExecutionFactory.create(pss.toString(), ds).execSelect().materialise();
        ds.end();
        HashMap<Resource,String> types = new HashMap<>();
        HalColors cs = new HalColors();
        UserColorsAndClasses ucac = new UserColorsAndClasses();
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            Resource key = qs.get("type").asResource();
            String color = ucac.getColor(key);
            if (color!=null) {
                types.put(key,ColorTools.Hex2RGBA(color));
            } else if (!types.containsKey(key)) {
                types.put(key,cs.removeFirst());
            }
        }
        pss = new ParameterizedSparqlString("""
            select distinct ?roc ?type ?label ?value ?name
            where {
                values (?roc) {?selected}
                graph ?roc {?roc hal:hasFeature ?feature .
                ?feature a ?type; rdfs:label ?label; rdf:value ?value
            }} order by ?roc
            """);        
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("rdfs", RDFS.getURI());
        pss.setNsPrefix("rdf", RDF.uri);        
        pss.setValues("selected", roc);
        ds.begin(ReadWrite.READ);
        rs = QueryExecutionFactory.create(pss.toString(), ds).execSelect().materialise();
        ds.end();
        String lroc = "";
        Model m = ModelFactory.createDefaultModel();
        int c = 0;
        Resource layer = m.createResource()
                    .addProperty(RDF.type, HAL.FeatureLayer)
                    .addLiteral(HAL.layerNum, c)
                    .addProperty(HAL.location, host+"/iiif/?iiif="+host+PathFinder.Path2URL(urn)+"/info.json")
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
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            if ((layer==null)||(!lroc.equals(qs.get("roc").asResource().getURI()))) {
                c++;
                lroc = qs.get("roc").asResource().getURI();
                layer = m.createResource()
                    .addProperty(RDF.type, HAL.FeatureLayer)
                    .addLiteral(HAL.layerNum, c)
                    .addProperty(HAL.location, host+"/halcyon/?iiif="+qs.get("roc").asResource().getURI()+"/info.json")
                    .addLiteral(SchemaDO.name, qs.contains("name")?qs.get("name").asLiteral().getString():"Name Unknown")
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
            }
            COLORSCHEME.addProperty(HAL.colors, m.createResource()
                    .addLiteral(SchemaDO.name, qs.get("label").asLiteral().getString())
                    .addLiteral(HAL.classid, qs.get("value").asLiteral().getInt())
                    .addLiteral(HAL.color, types.get(qs.get("type").asResource()))
            );
        }
        Dataset dss = DatasetFactory.createGeneral();
        dss.getDefaultModel().add(m);
        RdfDataset rds = JenaTitanium.convert(dss.asDatasetGraph());
        RdfToJsonld rtj = RdfToJsonld.with(rds);
        JsonArray ja;
        String hold = null;
        try {
            ja = rtj.useNativeTypes(true).build();
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
            Logger.getLogger(FeatureManager.class.getName()).log(Level.SEVERE, null, ex);
        }
      return HFrame.wow(hold);
    }
}
