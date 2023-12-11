package com.ebremer.halcyon.wicket.ethereal;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.JsonLdVersion;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.serialization.RdfToJsonld;
import com.apicatalog.rdf.RdfDataset;
import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.ns.HAL;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.JenaTitanium;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.XSD;
import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.request.resource.PackageResourceReference;

/**
 *
 * @author erich
 */
public class Graph3D extends BasePage {
    private static final long serialVersionUID = 102163948377788566L;
    
    public Graph3D() {
        ListClasses cc = new ListClasses("chosen");
        add(cc);
        Button button = new Button("button", org.apache.wicket.model.Model.of("Update"));
        button.add(new AjaxEventBehavior("click") {
            @Override
            protected void onEvent(AjaxRequestTarget target) {
                String zam = getData(cc.getClasses());
                System.out.println(zam);
                target.appendJavaScript("console.log(\"UPDATE GRAPH\"); Graph.graphData("+zam+");");
            }
        });
        add(button);
    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(JavaScriptHeaderItem.forReference(new PackageResourceReference(getClass(),"three.js")));
        response.render(JavaScriptHeaderItem.forReference(new PackageResourceReference(getClass(),"CSS2DRenderer.js")));
        response.render(JavaScriptHeaderItem.forReference(new PackageResourceReference(getClass(),"3d-force-graph.min.js")));
        response.render(JavaScriptHeaderItem.forReference(new PackageResourceReference(getClass(),"three-spritetext.min.js")));
    }

    public String getData(List<RDFNode> list) {
        try {
            //Dataset xs = DataCore.getInstance().getDataset();
            Dataset xs = DatasetFactory.create();
            Model b = ModelFactory.createDefaultModel();
            try {
                RDFDataMgr.read(b, new FileInputStream("morph.ttl"), Lang.TURTLE);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(Graph3D.class.getName()).log(Level.SEVERE, null, ex);
            }
            System.out.println("DATASET LOADED : "+b.size());
            xs.getDefaultModel().add(b);
            ParameterizedSparqlString pss;
            /*
            pss = new ParameterizedSparqlString(
                """
                construct {
                    res:graph3d
                        a :GraphType;
                          :hasNode ?s;
                          :hasNode ?o;
                          :hasLink ?link .
                    ?s
                        :id ?sString;
                        :subject ?sString;
                        :group 1 .
                    ?o
                        :id ?oString;
                        :group 1;
                        :value ?oString .
                    ?link
                        :source ?sString;
                        :target ?oString;
                        :predicate ?pString .
                    } where {
                        ?s ?p ?o #graph ?g {?s ?p ?o}
                        values (?p) {?typelist}
                        filter (!isLiteral(?o))
                        bind(str(?s) as ?sString)
                        bind(str(?p) as ?pString)
                        bind(str(?o) as ?oString)
                        bind(bnode() as ?link)
                    } limit 500
                """);*/
            pss = new ParameterizedSparqlString(
                """
                construct {
                    res:graph3d
                        a :GraphType;
                          :hasNode ?s;
                          :hasNode ?o;
                          :hasLink ?link .
                    ?s
                        :id ?sString;
                        :subject ?sString;
                        :group 0 .
                    ?o
                        :id ?oString;
                        :group ?group;
                        :value ?oString .
                    ?link
                        :source ?sString;
                        :target ?oString;
                        :predicate ?pString .
                    } where {
                        ?s so:name ?sname; a ?type; ?p ?o .
                        ?o so:name ?oname .
                        optional{?o hal:group ?group}
                        values (?p) {?typelist}
                        bind(str(?sname) as ?sString)
                        bind(substr(str(?p),20) as ?pString)
                        bind(str(?oname) as ?oString)
                        bind(bnode() as ?link)
                    } limit 5000
                """);
            pss.setNsPrefix("", "http://www.ebremer.com/ns/");
            pss.setNsPrefix("rdf", RDF.uri);
            pss.setNsPrefix("xsd", XSD.NS);
            pss.setNsPrefix("hal", HAL.NS);
            pss.setNsPrefix("so", SchemaDO.NS);
            pss.setNsPrefix("res", "http://www.ebremer/com/resource/");
            pss.setValues("typelist", list);
            QueryExecution qe = QueryExecutionFactory.create(pss.toString(), xs);
            //xs.begin(ReadWrite.READ);
            Model m = qe.execConstruct();
            //xs.end();
            Dataset ds = DatasetFactory.createGeneral();
            ds.getDefaultModel().add(m);
            RdfDataset rds = JenaTitanium.convert(ds.asDatasetGraph());
            RdfToJsonld rtj = RdfToJsonld.with(rds).useNativeTypes(true);
            JsonArray ja = rtj.build();
            JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JsonWriter out = writerFactory.createWriter(baos);
            String frame =
                """
                {
                    "@context":{
                        "hal":"http://www.ebremer.com/ns/",
                        "GraphType": "hal:GraphType",
                        "group": "hal:group",
                        "id": "hal:id",
                        "source": "hal:source",
                        "target": "hal:target",
                        "nodes": "hal:hasNode",
                        "links": "hal:hasLink",
                        "predicate": "hal:predicate"
                    },
                    "@omitDefault": true,
                    "@explicit": true,
                    "@requireAll": true,
                    "@embed": "@always",
                    "@type": "GraphType",
                    "nodes": {"@embed": "@always"},
                    "links": {"@embed": "@always"}
                }
                """;
            JsonLdOptions options = new JsonLdOptions();
            options.setUseNativeTypes(true);
            options.setProcessingMode(JsonLdVersion.V1_1);
            Document contextDocument = JsonDocument.of(new ByteArrayInputStream(frame.getBytes()));
            JsonObject jo = JsonLd.frame(JsonDocument.of(ja), contextDocument).options(options).get();
            out.writeObject(jo);
            String yay = new String(baos.toByteArray());
            System.out.println("RESULTS :\n"+yay);
            return yay;
        } catch (JsonLdError ex) {
            Logger.getLogger(Graph3D.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
}

/*
            String frame = """
                           {
                                "@context":{
                                    "hal":"http://www.ebremer.com/ns/",
                                    "GraphType": "hal:GraphType",
                                    "group": "hal:group",
                                    "id": "hal:id",
                                    "source": "hal:source",
                                    "target": "hal:target",
                                    "nodes": "hal:hasNode",
                                    "links": "hal:hasLink",
                                    "predicate": "hal:predicate"
                                },
                                "@omitDefault": true,
                                "@explicit": true,
                                "@requireAll": true,
                                "@embed": "@always",
                                "@type": "GraphType",
                                "nodes": {"@embed": "@always"},
                                "links": {"@embed": "@always"}
                           }
                           """;
            JsonLdOptions options = new JsonLdOptions();
            options.setUseNativeTypes(true);
            options.setProcessingMode(JsonLdVersion.V1_1);
            Document contextDocument = JsonDocument.of(new ByteArrayInputStream(frame.getBytes()));
            //JsonObject jo = JsonLd.compact(JsonDocument.of(ja), contextDocument).get();
            JsonStructure js;
            RdfToJsonld aa;
            //js = JsonLd.flatten(JsonDocument.of(ja)).options(options).get();
            //js = JsonLd.compact(JsonDocument.of(ja), contextDocument).options(options).get();
            JsonObject jo = JsonLd.frame(JsonDocument.of(ja), contextDocument).options(options).get();
            //JsonObject jo = JsonLd.frame(JsonDocument.of(js.asJsonArray()), contextDocument).options(options).get();
            out.writeObject(jo);
            //out.writeArray(js.asJsonArray());
            //out.writeObject(jsrc.asJsonObject());
            srcdata = new String(baos.toByteArray());
            //System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
            //System.out.println(srcdata);
            //System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
*/