package com.ebremer.halcyon.imagebox;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.JsonLdVersion;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.document.RdfDocument;
import com.ebremer.halcyon.filereaders.ROCImageReader;
import static com.ebremer.halcyon.imagebox.IIIFUtils.IIIFAdjust;
import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.lib.ImageMeta.ImageScale;
import com.ebremer.halcyon.filereaders.ImageReader;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.GEO;
import com.ebremer.ns.IIIF;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class IIIFMETA {
    
    public static String GetImageInfo(URI uri, ImageMeta meta) {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("so", SchemaDO.NS);
        Resource s = m.createResource(uri.toString());
        s.addProperty(SchemaDO.name, getTitle(meta.getRDF()));
        m.addLiteral(s, EXIF.height, meta.getHeight());
        m.addLiteral(s, EXIF.width, meta.getWidth());
        //m.addLiteral(s, EXIF.xResolution, Math.round(10000/mppx));
        //m.addLiteral(s, EXIF.yResolution, Math.round(10000/mppy));
        m.addLiteral(s, EXIF.resolutionUnit, 3);
        Resource scale = m.createResource();
        ArrayList<ImageScale> scales = meta.getScales();
        //System.out.println(scales);
        //for (int j=scales.size()-1;j>=0;j--) {
        for (int j=0;j<scales.size();j++) {
            //System.out.println("SCALE --> "+scales.get(j));
            Resource size = m.createResource();
            m.addLiteral(size,IIIF.width,scales.get(j).width());
            m.addLiteral(size,IIIF.height,scales.get(j).height());
            m.add(s,IIIF.sizes,size);
            m.addLiteral(scale, IIIF.scaleFactors,scales.get(j).scale());
            m.add(s,IIIF.tiles,scale);            
        }
        m.addLiteral(scale,IIIF.width,meta.getTileSizeX());
        m.addLiteral(scale,IIIF.height,meta.getTileSizeY());
        IIIFUtils.addSupport(s, m);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RDFDataMgr.write(baos, m, Lang.NTRIPLES);
        String raw = new String(baos.toByteArray(), UTF_8);
        Document rdf;
        JsonArray ja;
        JsonLdOptions options = new JsonLdOptions();
        options.setOrdered(false);
        options.setUseNativeTypes(true);
        options.setOmitGraph(true);
        try {
            rdf = RdfDocument.of(new ByteArrayInputStream(raw.getBytes()));
            ja = JsonLd.fromRdf(rdf)
                .options(options)
                .get();
            Document contextDocument = JsonDocument.of(new ByteArrayInputStream(IIIF.CONTEXT.getBytes()));
            JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
            baos = new ByteArrayOutputStream();
            JsonWriter out = writerFactory.createWriter(baos);
            JsonObject jo = JsonLd.compact(JsonDocument.of(ja), contextDocument)
                               .mode(JsonLdVersion.V1_1)
                               .options(options)
                               .get();
            //out.writeObject(jo);
            jo = JsonLd.frame(JsonDocument.of(jo), JsonDocument.of(
                new ByteArrayInputStream((
                    """
                    {
                    "@context": "http://iiif.io/api/image/2/context.json",
                    "@embed": "@always",
                    "protocol": "http://iiif.io/api/image",
                    "profile": {}
                    }
                    """).getBytes())))              
                        .mode(JsonLdVersion.V1_1)
                        .options(options)
                       .get();
            out.writeObject(jo);
            return IIIFAdjust(IIIFUtils.NSFixes(new String(baos.toByteArray(), UTF_8)));
        } catch (JsonLdError ex) {
            Logger.getLogger(IIIFMETA.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public static String getTitle(Model mm) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?title
            where { ?fc a geo:FeatureCollection; dct:title ?title }
            limit 1
            """
        );
        pss.setNsPrefix("geo", GEO.NS);
        pss.setNsPrefix("dct", DCTerms.NS);
        ResultSet rsx = QueryExecutionFactory.create(pss.toString(), mm).execSelect();
        if (rsx.hasNext()) {
            return rsx.next().get("title").asLiteral().getString();
        }
        return "Unknown";
    }
    
    public static void main(String[] args) throws Exception {
        File file2 = new File("/HalcyonStorage/nuclearsegmentation2019/coad/TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.zip");
        URI uri = file2.toURI();
        ImageReader ir = new ROCImageReader(uri,null);
        ImageMeta meta = ir.getImageMeta();
        System.out.println(GetImageInfo(new URI("https://beak.bmi.stonybrook.edu/iiif/?iiif=https://beak.bmi.stonybrook.edu/Storage/images/tcga_data/ov/TCGA-04-1342-01A-01-TS1.66421418-fc94-4215-9ab1-6398f710f6ca.svs"),meta));
    }
}
