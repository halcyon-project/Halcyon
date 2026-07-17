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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class HFrame {
    private static final Logger logger = LoggerFactory.getLogger(HFrame.class);
    
    public static final String VIEWERCONTEXT =
        """
        {
            "@context": [
                {
                    "so": "https://schema.org/",
                    "csvw": "https://www.w3.org/ns/csvw/",
                    "hal": "https://halcyon.is/ns/",
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
                        "@id": "https://halcyon.is/ns/colors",
                        "@container": "@set"
                    },
                    "high": "hal:high",
                    "haslayer": {
                        "@id": "https://halcyon.is/ns/haslayer",
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
            logger.error(ex.toString());
        }
        return jo;
    }
    
    public static Document getViewerContext() {
        try {
            return JsonDocument.of(new ByteArrayInputStream(HFrame.VIEWERCONTEXT.getBytes()));
        } catch (JsonLdError ex) {
            logger.error(ex.toString());
        }
        return null;
    }
    
    /**
     * L11: guards the two nulls this walked straight into. {@code null} json NPE'd
     * in {@code new StringReader(null)}, and — the one that actually happened —
     * input without a {@code haslayer} key made {@code getJsonArray} return null,
     * which {@code writeArray(null)} then NPE'd on. Live via {@code FeatureManager},
     * so a feature set that simply had no layers took the caller down. An absent
     * layer list is an empty layer list, not a failure.
     */
    public static String wow(String json) {
        if (json == null || json.isBlank()) {
            return "[]";
        }
        JsonArray ja;
        try (JsonReader jr = Json.createReader(new StringReader(json))) {
            ja = jr.readObject().getJsonArray("haslayer");
        }
        if (ja == null) {
            return "[]";
        }
        JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JsonWriter out = writerFactory.createWriter(baos)) {
            out.writeArray(ja);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
