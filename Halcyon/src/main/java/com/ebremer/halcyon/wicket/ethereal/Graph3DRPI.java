package com.ebremer.halcyon.wicket.ethereal;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.JsonLdVersion;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.serialization.RdfToJsonld;
import com.apicatalog.rdf.RdfDataset;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
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
import org.apache.wicket.markup.html.form.Button;
import org.springframework.core.io.ClassPathResource;

/**
 *
 * @author erich
 */
public class Graph3DRPI extends BasePage {
    private static final long serialVersionUID = 102163948377788566L;
    
    public Graph3DRPI() {
        Button button = new Button("button", org.apache.wicket.model.Model.of("Update"));
        button.add(new AjaxEventBehavior("click") {
            @Override
            protected void onEvent(AjaxRequestTarget target) {
                String zam = getData();
                //System.out.println(zam);
                target.appendJavaScript("console.log(\"UPDATE GRAPH\"); Graph.graphData("+zam+");");
            }
        });
        add(button);
    }

    public String getData() {
        try {
            String RPI = "rpi2.ttl";
            Model rpi = ModelFactory.createDefaultModel(); 
            ClassPathResource cpr = new ClassPathResource(RPI);
            try {
                RDFDataMgr.read(rpi, cpr.getInputStream(), Lang.TURTLE);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(Graph3DRPI.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(Graph3DRPI.class.getName()).log(Level.SEVERE, null, ex);
            }
            System.out.println("TRIPLES : "+rpi.size());
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
                        ?s ?p ?o .
                        optional{?o hal:group ?group; so:name ?oname}
                        bind(str(?s) as ?sString)
                        bind(substr(str(?p),20) as ?pString)
                        bind(str(?o) as ?oString)
                        bind(bnode() as ?link)
                        #values ?p {rpi:doc rpi:authors rpi:author rpi:publication rpi:doc rpi:name rpi:subjects}
                        filter (!isLiteral(?o))
                        #filter (?p!=rpi:faculty)
                        #filter (?p!=rpi:data)
                        filter (?s!=<file:///D:/RPI/scopus/scopus-all.xml>)
                    } #limit 500
                """);
            pss.setNsPrefix("rpi", "https://rpi.edu/ns/");
            pss.setNsPrefix("", HAL.NS);
            pss.setNsPrefix("rdf", RDF.uri);
            pss.setNsPrefix("xsd", XSD.NS);
            pss.setNsPrefix("hal", HAL.NS);
            pss.setNsPrefix("so", SchemaDO.NS);
            QueryExecution qe = QueryExecutionFactory.create(pss.toString(), rpi);
            Model m = qe.execConstruct();
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
                        ?s a ?type; ?p ?o .
                        optional{?o hal:group ?group; so:name ?oname}
                        bind(str(?s) as ?sString)
                        bind(substr(str(?p),20) as ?pString)
                        bind(bnode() as ?link)
                        bind(bnode() as ?literal)
                        bind(str(?literal) as ?oString)
                        filter (isLiteral(?o))
                    }
                """);
            pss.setNsPrefix("rpi", "https://rpi.edu/ns/");
            pss.setNsPrefix("", HAL.NS);
            pss.setNsPrefix("rdf", RDF.uri);
            pss.setNsPrefix("xsd", XSD.NS);
            pss.setNsPrefix("hal", HAL.NS);
            pss.setNsPrefix("so", SchemaDO.NS);
            System.out.println("SPARQL :\n"+pss.toString());
            qe = QueryExecutionFactory.create(pss.toString(), m);
            Model m2 = qe.execConstruct();       
            //System.out.println("=================================================================================");
            //RDFDataMgr.write(System.out, m2, Lang.TURTLE);
            //System.out.println("=================================================================================");            
            m.add(m2);
            System.out.println("TRIPLES : "+m.size());
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
            Logger.getLogger(Graph3DRPI.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
}
