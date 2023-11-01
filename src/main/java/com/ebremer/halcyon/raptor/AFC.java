package com.ebremer.halcyon.raptor;

import java.io.File;
import java.io.IOException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdEmbed;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.JsonLdVersion;
import com.apicatalog.jsonld.api.CompactionApi;
import com.apicatalog.jsonld.api.FramingApi;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.document.RdfDocument;
import com.apicatalog.jsonld.lang.Keywords;
import com.apicatalog.jsonld.processor.FromRdfProcessor;
import com.apicatalog.rdf.RdfDataset;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonStructure;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.JsonLDWriteContext;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.RDFWriterBuilder;
import org.apache.jena.riot.system.JenaTitanium;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.writer.JsonLD11Writer;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class AFC {
    private static Map<String, ?> configIndented = Map.of(JsonGenerator.PRETTY_PRINTING, true);
    private static Map<String, ?> configFlat = Map.of();
    
    private static Map<String, ?> config(boolean indented) {
        return indented ? configIndented : configFlat;
    }
    
    public static void main(String[] args) throws IOException, JsonLdError {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = (Logger) LoggerFactory.getLogger("ROOT");
        logger.setLevel(Level.OFF);

        //File x = new File("D:\\tcga\\cvpr-data\\rdf\\coad\\TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.ttl.gz");
        File xx = new File("/AAA/fc.ttl");
        Dataset dsx = DatasetFactory.create();
        try (
            FileInputStream fis = new FileInputStream(xx);
            //GZIPInputStream gis = new GZIPInputStream(fis);
        ) {
            RDFDataMgr.read(dsx.getDefaultModel(), fis, Lang.TURTLE);
        }
        
        dsx.getPrefixMapping().setNsPrefix("contributor", "dcterms:contributor");
        dsx.getPrefixMapping().setNsPrefix("date", "dcterms:date");
        dsx.getPrefixMapping().setNsPrefix("description", "dcterms:description");
        dsx.getPrefixMapping().setNsPrefix("references", "dcterms:references");
        //dsx.getPrefixMapping().setNsPrefix("creator", "dcterms:creator");
        dsx.getPrefixMapping().setNsPrefix("source", "dcterms:source");
        //dsx.getPrefixMapping().setNsPrefix("publisher", "dcterms:publisher");
        dsx.getPrefixMapping().setNsPrefix("asWKT", "geo:asWKT");
        dsx.getPrefixMapping().setNsPrefix("Feature", "geo:Feature");
        dsx.getPrefixMapping().setNsPrefix("geometry", "geo:hasGeometry");
        dsx.getPrefixMapping().setNsPrefix("FeatureCollection", "geo:FeatureCollection");
        dsx.getPrefixMapping().setNsPrefix("hasProbability", "hal:hasProbability");
        dsx.getPrefixMapping().setNsPrefix("height", "exif:height");
        dsx.getPrefixMapping().setNsPrefix("width", "exif:width");
        dsx.getPrefixMapping().setNsPrefix("features", "rdfs:member");
        dsx.getPrefixMapping().setNsPrefix("hasProbability", "hal:hasProbability");
        dsx.getPrefixMapping().setNsPrefix("classification", "hal:classification");
        DatasetGraph dsg = dsx.asDatasetGraph();
        RdfDataset ds = JenaTitanium.convert(dsg);
        Document doc = RdfDocument.of(ds);
        JsonLdOptions options = new JsonLdOptions();
            options.setOrdered(false);
            options.setUseNativeTypes(true);
            options.setOmitGraph(true);
            options.setExplicit(true);
            options.setRequiredAll(false);
            options.setEmbed(JsonLdEmbed.ALWAYS);
        JsonArray ja = FromRdfProcessor.fromRdf(doc, options);
        jakarta.json.JsonObject writeRdf = Json.createObjectBuilder()
                .add(Keywords.GRAPH, ja)
                .build();
        JsonObjectBuilder cxt = Json.createObjectBuilder();
        JsonObjectBuilder neocontext = Json.createObjectBuilder();
          dsg.prefixes().forEach((k, v) -> {
            if ( ! k.isEmpty() )
                neocontext.add(k, v);
        });
        //neocontext.add("id","@id");
        //neocontext.add("type","@type");
        neocontext.add("creator", Json.createObjectBuilder().add(Keywords.ID, "dcterms:creator").add(Keywords.TYPE, Keywords.ID));
        neocontext.add("publisher", Json.createObjectBuilder().add(Keywords.ID, "dcterms:publisher").add(Keywords.TYPE, Keywords.ID));
        neocontext.add("date", Json.createObjectBuilder().add(Keywords.ID, "dcterms:date").add(Keywords.TYPE, XSD.dateTime.getURI()));
        cxt.add(Keywords.CONTEXT, neocontext);
        cxt.add(Keywords.EMBED, Keywords.ALWAYS);
        cxt.add(Keywords.EXPLICIT, false);
        cxt.add(Keywords.OMIT_DEFAULT, true);
        cxt.add(Keywords.REQUIRE_ALL, false);
        cxt.add(Keywords.TYPE, Json.createArrayBuilder().add("FeatureCollection"));
        jakarta.json.JsonObject context = cxt.build();   
        Document contextDoc = JsonDocument.of(context);
        FramingApi api = JsonLd.frame(JsonDocument.of(writeRdf), contextDoc);
        JsonStructure x = api
                            .omitGraph(true)
                            .mode(JsonLdVersion.V1_1)
                          //  .options(options)
                            .get();
        Map<String,?> config = config(true);
        JsonWriterFactory factory = Json.createWriterFactory(config);
        try (JsonWriter writer = factory.createWriter(System.out)) {
            writer.write(x);
        }
    }
}
