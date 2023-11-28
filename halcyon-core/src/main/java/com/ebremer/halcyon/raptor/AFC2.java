package com.ebremer.halcyon.raptor;

import java.io.File;
import java.io.IOException;
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
import com.apicatalog.rdf.RdfTriple;
import com.apicatalog.rdf.spi.RdfProvider;
import com.ebremer.ns.HAL;
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
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
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
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class AFC2 {
    private static Map<String, ?> configIndented = Map.of(JsonGenerator.PRETTY_PRINTING, true);
    private static Map<String, ?> configFlat = Map.of();
    
    private static Map<String, ?> config(boolean indented) {
        return indented ? configIndented : configFlat;
    }
    
    public static void main(String[] args) throws IOException, JsonLdError {
       // LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        //Logger logger = (Logger) LoggerFactory.getLogger("ROOT");
        //logger.setLevel(Level.OFF);
        Dataset dsx = DatasetFactory.create();
        Model mm = dsx.getDefaultModel();
        Resource me = mm.createResource("http://www.ebremer.com/me");
        Statement s = mm.createLiteralStatement(me, FOAF.age, 55);
        mm.add(s);
        Resource reif = mm.createResource(s).addLiteral(HAL.hasProbability, 0.84f);
        RDFDataMgr.write(System.out, mm, Lang.TURTLE);
        
        DatasetGraph dsg = dsx.asDatasetGraph();
        
        RdfProvider provider = RdfProvider.provider();
        RdfDataset rdfDataset = provider.createDataset();
        
        

    }
}
