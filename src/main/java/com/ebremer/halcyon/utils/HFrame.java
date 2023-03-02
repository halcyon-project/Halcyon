package com.ebremer.halcyon.utils;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.JsonLdVersion;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author erich
 */
public class HFrame {
    
    public static final String VIEWERCONTEXT =
        """
        {
            "@context": [
                {
                    "so": "https://schema.org/",
                    "csvw": "https://www.w3.org/ns/csvw/",
                    "hal": "https://www.ebremer.com/halcyon/ns/",
                    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                    "label": {"@id": "rdfs:label"},
                    "value": {"@id": "rdf:value"},
                    "ColorScheme": "hal:ColorScheme",
                    "colorscheme": "hal:colorscheme",
                    "colorspectrum": "hal:colorspectrum",
                    "location": "hal:location",
                    "color": "hal:color",
                    "name": "so:name",
                    "layerNum": "hal:layerNum",
                    "colors": {
                        "@id": "https://www.ebremer.com/halcyon/ns/colors",
                        "@container": "@set"
                    },
                    "high": "hal:high",
                    "haslayer": {
                        "@id": "https://www.ebremer.com/halcyon/ns/haslayer",
                        "@container": "@set"
                    },
                    "low": "hal:low",
                    "opacity": "hal:opacity",
                    "classid": "hal:classid",
                    "FeatureLayer": "hal:FeatureLayer",
                    "LayerSet": "hal:LayerSet"
                }
            ],
            "@omitDefault": false,
            "@explicit": false,
            "@requireAll": true,
            "@embed": "@always",
            "@type": "LayerSet"   
        }
        """;
    
    public static JsonObject frame(JsonObject jo, JsonLdOptions options) {
        try {
            jo = JsonLd.frame(JsonDocument.of(jo), getViewerContext())
                    .mode(JsonLdVersion.V1_1)
                    .options(options)
                    .get();
        } catch (JsonLdError ex) {
            Logger.getLogger(HFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
        return jo;
    }
    
    public static Document getViewerContext() {
        try {
            return JsonDocument.of(new ByteArrayInputStream(HFrame.VIEWERCONTEXT.getBytes()));
        } catch (JsonLdError ex) {
            Logger.getLogger(HFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public static String wow(String json) {
        JsonReader jr = Json.createReader(new StringReader(json));
        JsonObject jo = jr.readObject();
        JsonArray ja = jo.getJsonArray("haslayer");
        JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonWriter out = writerFactory.createWriter(baos);
        out.writeArray(ja);
        return new String(baos.toByteArray());
    }
}
