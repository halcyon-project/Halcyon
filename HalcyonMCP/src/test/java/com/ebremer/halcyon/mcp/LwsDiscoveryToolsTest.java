package com.ebremer.halcyon.mcp;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP-10: both tools refuse without a caller, and the Type Search projection
 * keeps the URIs a client needs while dropping structural terms. (Whole-storage
 * ACP filtering is the storage's job, runtime-verified.)
 */
class LwsDiscoveryToolsTest {

    private static JsonObject parse(String s) {
        try (var r = Json.createReader(new StringReader(s))) {
            return r.readObject();
        }
    }

    @Test
    void bothToolsRefuseWithoutAVerifiedCaller() {
        assertThrows(IllegalStateException.class,
                () -> new LwsDiscoveryTools().findSlides(new ToolContext(Map.of())));
        assertThrows(IllegalStateException.class,
                () -> new LwsDiscoveryTools().listStacks(new ToolContext(Map.of())));
    }

    @Test
    void theSearchedTypesAreTheScannerDiscoveredOnes() {
        assertEquals("https://schema.org/ImageObject", LwsDiscoveryTools.IMAGE_OBJECT);
        assertEquals("https://halcyon.is/zephyr/ns/Stack", LwsDiscoveryTools.ZEPH_STACK);
    }

    @Test
    void matchProjectionKeepsUriAndMediaTypeDropsStructuralTerms() {
        JsonObject body = parse("""
            {"totalItems":1,"items":[
              {"id":"https://s/slides/a.svs","mediaType":"image/tiff",
               "type":["DataResource","https://schema.org/ImageObject"]}
            ]}""");
        JsonArray matches = LwsDiscoveryTools.matchItems(body);
        assertEquals(1, matches.size());
        JsonObject m = matches.getJsonObject(0);
        assertEquals("https://s/slides/a.svs", m.getString("uri"));
        assertEquals("image/tiff", m.getString("mediaType"));
        assertTrue(m.getJsonArray("types").toString().contains("ImageObject"));
        assertFalse(m.getJsonArray("types").toString().contains("DataResource"),
                "the structural term must be dropped");
    }

    @Test
    void emptyOrAbsentItemsGiveAnEmptyMatchList() {
        assertTrue(LwsDiscoveryTools.matchItems(parse("{\"totalItems\":0}")).isEmpty());
        assertTrue(LwsDiscoveryTools.matchItems(parse("{\"items\":[]}")).isEmpty());
    }
}
