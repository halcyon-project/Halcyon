package com.ebremer.halcyon.imagebox;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.JsonLdVersion;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.document.RdfDocument;
import static com.ebremer.halcyon.imagebox.IIIFUtils.IIIFAdjust;
import com.ebremer.halcyon.imagebox.TE.ImageMeta;
import com.ebremer.halcyon.imagebox.TE.ImageMeta.ImageScale;
import com.ebremer.halcyon.imagebox.TE.ImageReader;
import com.ebremer.ns.EXIF;
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
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

/**
 *
 * @author erich
 */
public class IIIFMETA {
    
    public static String GetImageInfo(URI uri, ImageMeta meta) {
        Model m = ModelFactory.createDefaultModel();
        Resource s = m.createResource(uri.toString());
        m.addLiteral(s, EXIF.height, meta.getHeight());
        m.addLiteral(s, EXIF.width, meta.getWidth());
        //m.addLiteral(s, EXIF.xResolution, Math.round(10000/mppx));
        //m.addLiteral(s, EXIF.yResolution, Math.round(10000/mppy));
        m.addLiteral(s, EXIF.resolutionUnit, 3);
        Resource scale = m.createResource();
        ArrayList<ImageScale> scales = meta.getScales();
        for (int j=scales.size()-1;j>=0;j--) {
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
    
    public static void main(String[] args) throws Exception {
        File file2 = new File("D:\\ATAN\\src\\TCGA-3C-AALI-01Z-00-DX1.F6E9A5DF-D8FB-45CF-B4BD-C6B76294C291.svs");
        //TileRequestEngine tpe2 = TileRequestEnginePool.getPool().borrowObject(file2.toURI());
        com.ebremer.halcyon.imagebox.TE.ImageReader ir = com.ebremer.halcyon.imagebox.TE.ImageReaderPool.getPool().borrowObject(file2.toURI());
        ImageMeta meta = ir.getMeta();
        com.ebremer.halcyon.imagebox.TE.ImageReaderPool.getPool().returnObject(file2.toURI(), (ImageReader) ir);
        System.out.println(GetImageInfo(new URI("https://beak.bmi.stonybrook.edu/iiif/?iiif=https://beak.bmi.stonybrook.edu/Storage/images/tcga_data/ov/TCGA-04-1342-01A-01-TS1.66421418-fc94-4215-9ab1-6398f710f6ca.svs"),meta));
    }
}
