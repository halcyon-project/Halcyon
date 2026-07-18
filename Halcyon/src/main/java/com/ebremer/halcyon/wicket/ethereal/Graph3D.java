package com.ebremer.halcyon.wicket.ethereal;

import com.ebremer.halcyon.gui.CspNonce;
import org.apache.wicket.markup.html.WebMarkupContainer;
import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.JsonLdVersion;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.serialization.RdfToJsonld;
import com.apicatalog.rdf.RdfDataset;
import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.acp.AcpSecuredDatasetGraph;
import com.ebremer.lws.acp.AcpSecurityEvaluator;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.ns.HAL;
import org.danekja.java.util.function.serializable.SerializableSupplier;
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
import org.apache.jena.riot.system.jsonld.JenaToTitanium;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.XSD;
import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.request.mapper.parameter.PageParameters;

/**
 * The 3d-force-graph explorer, pointable at EITHER triple store — the same
 * {@code ?endpoint=rdf2} convention as the SPARQL page: {@code /threed} walks
 * the classic store, {@code /threed?endpoint=rdf2} walks the W3C LWS store.
 * The LWS side is never the raw dataset: it is the caller's own ACP-secured
 * view, so the graph shows exactly the resources this user could read over
 * the LWS API and the internal graphs do not exist to be drawn.
 *
 * @author erich
 */
public class Graph3D extends BasePage {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Graph3D.class);
    private static final long serialVersionUID = 102163948377788566L;

    /** Which store this instance draws. Allowlisted from the page parameter. */
    private final boolean lws;

    public Graph3D() {
        this(new PageParameters());
    }

    public Graph3D(PageParameters parameters) {
        this.lws = "rdf2".equals(parameters.get("endpoint").toString(""));

        add(new Label("which", lws ? "the W3C LWS store (lws-tdb2)" : "the Halcyon store (tdb2)"));
        BookmarkablePageLink<Void> classic = new BookmarkablePageLink<>("classic", Graph3D.class);
        classic.setEnabled(lws);
        add(classic);
        BookmarkablePageLink<Void> lwsLink = new BookmarkablePageLink<>("lws", Graph3D.class,
                new PageParameters().add("endpoint", "rdf2"));
        lwsLink.setEnabled(!lws);
        add(lwsLink);

        ListClasses cc = new ListClasses("chosen", datasetSupplier(lws));
        add(cc);
        Button button = new Button("button", org.apache.wicket.model.Model.of("Update"));
        button.add(new AjaxEventBehavior("click") {
            @Override
            protected void onEvent(AjaxRequestTarget target) {
                String zam = getData(cc.getClasses());
                logger.debug("{}", zam);
                target.appendJavaScript("console.log(\"UPDATE GRAPH\"); Graph.graphData("+zam+");");
            }
        });
        add(button);
    }

    /**
     * The chosen store as a per-call dataset: the classic {@code DataCore},
     * or the LWS store through the CALLER's ACP-secured view — never raw
     * (the evaluator is built fresh per call, per its own contract, and the
     * ACP decision stays live).
     */
    static SerializableSupplier<Dataset> datasetSupplier(boolean lws) {
        return lws ? Graph3D::lwsDataset : () -> DataCore.getInstance().getDataset();
    }

    private static Dataset lwsDataset() {
        LwsStore store = LwsStore.get();
        HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
        AgentContext agent = hp != null && !hp.isAnon() && hp.getUserURI() != null
                ? new AgentContext(hp.getUserURI(), null, null, null)
                : AgentContext.PUBLIC;
        return DatasetFactory.wrap(new AcpSecuredDatasetGraph(store.raw().asDatasetGraph(),
                new AcpSecurityEvaluator(agent, new AcpEngine(store))));
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
            Dataset xs = datasetSupplier(lws).get();
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
            // H13: guarded end() + a closed QueryExecution.
            Model m;
            xs.begin(ReadWrite.READ);
            try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), xs)) {
                m = qe.execConstruct();
            } finally {
                xs.end();
            }
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
            logger.debug("SPARQL :\n{}", pss.toString());
            // H13: this used to REASSIGN the `qe` above, so the first execution was
            // dropped on the floor unclosed, and this one was never closed either.
            // The debug dump also sat INSIDE the transaction — execConstruct()
            // materialises into a Model, so it does not need one; it now runs after
            // end(), which keeps the transaction window as small as possible.
            Model m2;
            xs.begin(ReadWrite.READ);
            try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), xs)) {
                m2 = qe.execConstruct();
            } finally {
                xs.end();
            }
            m.add(m2);
            Dataset ds = DatasetFactory.createGeneral();
            ds.getDefaultModel().add(m);
            JsonLdOptions fromRdfOptions = new JsonLdOptions();
            fromRdfOptions.setUseNativeTypes(true);
            JsonArray ja = JenaToTitanium.convert(ds.asDatasetGraph(), fromRdfOptions);
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
            logger.debug("RESULTS :\n{}", yay);
            return yay;
        } catch (JsonLdError ex) {
            Logger.getLogger(Graph3D.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    /**
     * C5: bind the inline <script> tags in this page's markup so they receive the
     * request's CSP nonce. Done in onInitialize rather than a constructor because
     * these classes have several constructors that do not delegate to one another —
     * onInitialize runs exactly once whichever was used.
     */
    @Override
    protected void onInitialize() {
        super.onInitialize();
        add(new WebMarkupContainer("cspImportMap").add(new CspNonce()));
        add(new WebMarkupContainer("cspModule").add(new CspNonce()));
    }
}
