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
           String jsonld = """
                           {
                             "@graph" : [ {
                               "@type" : "adm:Role",
                               "@id" : "bdr:R8LS12819",
                               "adm:logEntry" : [ {
                                 "adm:logMessage" : {
                                   "@language" : "en",
                                   "@value" : "added eng desc"
                                 }
                               } ]
                             } ],
                             "@context" : {
                               "adm" : "http://purl.bdrc.io/ontology/admin/"
                             }
                           }""";
            String frame = """
                           {
                             "@context" : {
                               "id" : "@id",
                               "adm" : "http://purl.bdrc.io/ontology/admin/"
                             },
                             "@type": "adm:Role"
                           }""";
            String frameNoIdAlias = """
                                    {
                                      "@context" : {
                                        "adm" : "http://purl.bdrc.io/ontology/admin/"
                                      },
                                      "@type": "adm:Role"
                                    }""";
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
