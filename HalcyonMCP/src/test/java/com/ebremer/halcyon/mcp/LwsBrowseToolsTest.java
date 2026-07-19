package com.ebremer.halcyon.mcp;

import jakarta.json.Json;
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
 * MCP-7. Two properties are unit-pinned without a live storage: the
 * open-proxy guard (a URI in no configured storage is refused — and with no
 * storage configured in the test dir, that is every URI, so the guard is
 * exercised), and the listing projection {@link LwsBrowseTools#formatListing}
 * — member kinds, discovered types (structural terms dropped), and the opaque
 * cursors passed through verbatim.
 */
class LwsBrowseToolsTest {

    private static JsonObject parse(String s) {
        try (var r = Json.createReader(new StringReader(s))) {
            return r.readObject();
        }
    }

    @Test
    void refusesAUriOutsideAnyConfiguredStorage() {
        // No caller context at all → refusal happens before any fetch.
        assertThrows(IllegalStateException.class, () -> new LwsBrowseTools()
                .list("https://evil.example/x", null, new ToolContext(Map.of())));
    }

    @Test
    void projectionSeparatesContainersResourcesAndDropsStructuralTypes() {
        String listing = """
            {"totalItems":2,"items":[
              {"id":"https://s/c/sub/","type":["Container"]},
              {"id":"https://s/c/onto.ttl","type":["DataResource","https://halcyon.is/zephyr/ns/Stack"],
               "mediaType":"text/turtle","size":1234,"modified":"2026-07-19T00:00:00Z"}
            ]}""";
        JsonObject out = parse(LwsBrowseTools.formatListing("https://s/c/", parse(listing),
                null, null, "OPAQUE-NEXT", "OPAQUE-LAST"));

        assertEquals(2, out.getJsonNumber("totalItems").longValue());
        var items = out.getJsonArray("items");
        assertEquals("container", items.getJsonObject(0).getString("kind"));
        JsonObject res = items.getJsonObject(1);
        assertEquals("resource", res.getString("kind"));
        assertEquals("text/turtle", res.getString("mediaType"));
        assertEquals(1234, res.getJsonNumber("size").longValue());
        assertEquals("https://halcyon.is/zephyr/ns/Stack",
                res.getJsonArray("types").getString(0), "discovered type kept");
        assertFalse(res.getJsonArray("types").toString().contains("DataResource"),
                "the structural DataResource term must be dropped");
    }

    @Test
    void cursorsArePassedThroughVerbatimAndOnlyWhenPresent() {
        JsonObject out = parse(LwsBrowseTools.formatListing("https://s/c/",
                parse("{\"items\":[]}"), null, null, "SEALED-NEXT", null));
        JsonObject cursors = out.getJsonObject("cursors");
        assertEquals("SEALED-NEXT", cursors.getString("next"),
                "the opaque next cursor must survive untouched");
        assertFalse(cursors.containsKey("first"), "absent cursors must not be fabricated");
        assertFalse(cursors.containsKey("prev"));
        assertTrue(out.getJsonArray("items").isEmpty());
    }
}
