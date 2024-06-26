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
import org.apache.wicket.markup.html.form.Button;

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
        //response.render(JavaScriptHeaderItem.forReference(new PackageResourceReference(getClass(),"three-module.js")));
        //response.render(JavaScriptHeaderItem.forReference(new PackageResourceReference(getClass(),"CSS2DRenderer.js")));
        //response.render(JavaScriptHeaderItem.forReference(new PackageResourceReference(getClass(),"3d-force-graph.min.js")));
        //response.render(JavaScriptHeaderItem.forReference(new PackageResourceReference(getClass(),"three-spritetext.mjs")));
    }

    public String getData(List<RDFNode> list) {
        try {
            Dataset xs = DataCore.getInstance().getDataset();
            ParameterizedSparqlString pss;
            pss = new ParameterizedSparqlString(
                """
                construct {
                    hal:graph3d
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
                        :predicate ?pString
                    } where {
                        graph ?g {
                            ?s a ?type; ?p ?o .
                            optional{?o hal:group ?group; so:name ?oname}
                            values (?p) {?typelist}
                            bind(str(?s) as ?sString)
                            bind(substr(str(?p),20) as ?pString)
                            bind(str(?o) as ?oString)
                            bind(bnode() as ?link)
                            filter (!isLiteral(?o))
                        }
                    }
                """);
            pss.setNsPrefix("", HAL.NS);
            pss.setNsPrefix("rdf", RDF.uri);
            pss.setNsPrefix("xsd", XSD.NS);
            pss.setNsPrefix("hal", HAL.NS);
            pss.setNsPrefix("so", SchemaDO.NS);
            pss.setValues("typelist", list);
            QueryExecution qe = QueryExecutionFactory.create(pss.toString(), xs);
            xs.begin(ReadWrite.READ);
            Model m = qe.execConstruct();
            xs.end();
            System.out.println("=================================================================================");
            RDFDataMgr.write(System.out, m, Lang.TURTLE);
            System.out.println("=================================================================================");
            pss = new ParameterizedSparqlString(
                """
                construct {
                    hal:graph3d
                        a :GraphType;
                          :hasNode ?s;
                          :hasNode ?literal;
                          :hasLink ?link .
                    ?s
                        :id ?sString;
                        :subject ?sString;
                        :group 0 .
                    ?literal
                        :id ?oString;
                        :group ?group;
                        :value ?o .
                    ?link
                        :source ?sString;
                        :target ?oString;
                        :predicate ?pString
                    } where {
                        graph ?g {
                            ?s a ?type; ?p ?o .
                            optional{?o hal:group ?group; so:name ?oname}
                            values (?p) {?typelist}
                            bind(str(?s) as ?sString)
                            bind(substr(str(?p),20) as ?pString)
                            bind(bnode() as ?link)
                            bind(bnode() as ?literal)
                            bind(str(?literal) as ?oString)
                            filter (isLiteral(?o))
                        }
                    }
                """);
            pss.setNsPrefix("", HAL.NS);
            pss.setNsPrefix("rdf", RDF.uri);
            pss.setNsPrefix("xsd", XSD.NS);
            pss.setNsPrefix("hal", HAL.NS);
            pss.setNsPrefix("so", SchemaDO.NS);
            pss.setValues("typelist", list);
            System.out.println("SPARQL :\n"+pss.toString());
            qe = QueryExecutionFactory.create(pss.toString(), xs);
            xs.begin(ReadWrite.READ);
            Model m2 = qe.execConstruct();       
            System.out.println("=================================================================================");
            RDFDataMgr.write(System.out, m2, Lang.TURTLE);
            System.out.println("=================================================================================");
            xs.end();
            m.add(m2);
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
                        "hal":"https://halcyon.is/ns/",
                        "GraphType": "hal:GraphType",
                        "group": "hal:group",
                        "id": "hal:id",
                        "source": "hal:source",
                        "target": "hal:target",
                        "nodes": "hal:hasNode",
                        "links": "hal:hasLink",
                        "predicate": "hal:predicate",
                        "subject": "hal:subject"
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
