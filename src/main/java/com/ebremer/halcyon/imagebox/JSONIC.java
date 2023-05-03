package com.ebremer.halcyon.imagebox;

import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import java.io.IOException;
import org.apache.jena.atlas.json.JsonParseException;

/**
 *
 * @author erich
 */
public class JSONIC {
     public void testJSONLDPruneBlank() throws JsonParseException, IOException, JsonLdError {
           String jsonld = "{\n" + 
                   "  \"@graph\" : [ {\n" + 
                   "    \"@type\" : \"adm:Role\",\n" + 
                   "    \"@id\" : \"bdr:R8LS12819\",\n" + 
                   "    \"adm:logEntry\" : [ {\n" + 
                   "      \"adm:logMessage\" : {\n" + 
                   "        \"@language\" : \"en\",\n" + 
                   "        \"@value\" : \"added eng desc\"\n" + 
                   "      }\n" + 
                   "    } ]\n" + 
                   "  } ],\n" + 
                   "  \"@context\" : {\n" + 
                   "    \"adm\" : \"http://purl.bdrc.io/ontology/admin/\"\n" + 
                   "  }\n" + 
                   "}";
            String frame = "{\n" + 
                    "  \"@context\" : {\n" + 
                    "    \"id\" : \"@id\",\n" + 
                    "    \"adm\" : \"http://purl.bdrc.io/ontology/admin/\"\n" + 
                    "  },\n" + 
                    "  \"@type\": \"adm:Role\"\n" + 
                    "}";
            String frameNoIdAlias = "{\n" + 
                    "  \"@context\" : {\n" + 
                    "    \"adm\" : \"http://purl.bdrc.io/ontology/admin/\"\n" + 
                    "  },\n" + 
                    "  \"@type\": \"adm:Role\"\n" + 
                    "}";
            Object jsonObject = JsonUtils.fromString(jsonld);
            JsonLdOptions opts = new JsonLdOptions();
            opts.setPruneBlankNodeIdentifiers(true);
            Object res = JsonLdProcessor.frame(jsonObject, JsonUtils.fromString(frameNoIdAlias), opts);
            System.out.println("result with no alias for @id:\n"+JsonUtils.toPrettyString(res)+"\n\n");
            res = JsonLdProcessor.frame(jsonObject, JsonUtils.fromString(frame), opts);
            System.out.println("result with alias for @id:\n"+JsonUtils.toPrettyString(res)+"\n\n");
    }
    
     public static void main(String[] args) throws JsonParseException, IOException {
        JSONIC a = new JSONIC();
        a.testJSONLDPruneBlank();
     }
    
}
