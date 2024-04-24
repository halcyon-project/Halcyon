package com.ebremer.halcyon.utils;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.api.CompactionApi;
import com.apicatalog.jsonld.api.FramingApi;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.document.RdfDocument;
import com.apicatalog.jsonld.lang.Keywords;
import com.apicatalog.jsonld.processor.FromRdfProcessor;
import com.apicatalog.rdf.RdfDataset;
import com.ebremer.ns.GEO;
import com.ebremer.ns.HAL;
import com.ebremer.ns.SNO;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonStructure;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.math.BigDecimal;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.JenaTitanium;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.vocabulary.XSD;

/**
 *
 * @author erich
 */
public class HalJsonLD {

    public static void GetPolygons(Model m, OutputStream xout) {
        try {
            m.setNsPrefix("hal", HAL.NS);
            m.setNsPrefix("geo", GEO.NS);
            m.setNsPrefix("sno", SNO.NS);
            m.setNsPrefix("xsd", XSD.NS);
            m.setNsPrefix("asWKT", "geo:asWKT");
            m.setNsPrefix("hasGeometry", "geo:hasGeometry");
            m.setNsPrefix("hasProbability", "hal:hasProbability");
            m.setNsPrefix("measurement", "hal:measurement");
            Dataset dsx = DatasetFactory.create(m);
            DatasetGraph dsg = dsx.asDatasetGraph();
            JsonObjectBuilder cxt = Json.createObjectBuilder();
            dsg.prefixes().forEach((k, v) -> {
                if ( ! k.isEmpty() )
                    cxt.add(k, v);
            });
            cxt.add("classification", Json.createObjectBuilder()
                    .add(Keywords.ID, "hal:classification")
                    .add(Keywords.TYPE, Keywords.ID)
            );
            RdfDataset ds = JenaTitanium.convert(dsg);
            Document doc = RdfDocument.of(ds);
            JsonLdOptions options = new JsonLdOptions();
            options.setOrdered(false);
            options.setUseNativeTypes(true);
            options.setOmitGraph(true);
            JsonArray array = FromRdfProcessor.fromRdf(doc, options);
            JsonObject frame = Json.createObjectBuilder()
                    .add(Keywords.CONTEXT, cxt)
                    .add(Keywords.EMBED, Keywords.ALWAYS)
                    .add(Keywords.OMIT_DEFAULT, true)
                    .add(Keywords.REQUIRE_ALL, true)
                    .add("classification", Json.createObjectBuilder())
                    .add("hasGeometry", Json.createObjectBuilder())
                    .add("measurement", Json.createObjectBuilder())
                    .build();
            FramingApi api = JsonLd.frame(JsonDocument.of(array), JsonDocument.of(frame));
            JsonStructure x = api.get();
            //ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
            JsonWriter out = writerFactory.createWriter(xout);
            out.write(x);
            //String txt = new String(baos.toByteArray(), UTF_8);
            //System.out.println(txt);
        } catch (JsonLdError ex) {
            Logger.getLogger(HalJsonLD.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void main(String args[]) throws FileNotFoundException, JsonLdError {
        Model mx = ModelFactory.createDefaultModel();
        FileInputStream fis = new FileInputStream("sample.ttl");
        RDFDataMgr.read(mx, fis , Lang.TURTLE);
        GetPolygons(mx, null);
    }
}
