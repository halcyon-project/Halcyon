package com.ebremer.halcyon.mcp;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP-13: the caller guard, action parsing/validation, and the AccessRequest
 * payload shape (the same document the storage panel posts). The POST to the
 * Data Sharing Service is runtime-verified.
 */
class LwsAccessToolsTest {

    private static JsonObject parse(String s) {
        try (var r = Json.createReader(new StringReader(s))) {
            return r.readObject();
        }
    }

    @Test
    void refusesWithoutAVerifiedCaller() {
        assertThrows(IllegalStateException.class, () -> new LwsAccessTools()
                .requestAccess("https://evil/x", "read", new ToolContext(Map.of())));
    }

    @Test
    void actionsDefaultToReadAndRejectUnknowns() {
        assertEquals(Set.of("read"), LwsAccessTools.parseActions(null));
        assertEquals(Set.of("read"), LwsAccessTools.parseActions("  "));
        assertEquals(new LinkedHashSet<>(java.util.List.of("read", "modify")),
                LwsAccessTools.parseActions("read, MODIFY"));
        assertThrows(IllegalArgumentException.class,
                () -> LwsAccessTools.parseActions("read,sudo"),
                "an unknown action must be refused, not filed");
    }

    @Test
    void accessRequestPayloadHasTheExpectedShape() {
        JsonObject doc = parse(LwsAccessTools.accessRequestJson(
                "https://localhost:8888/user/alice#me",
                "https://s/c/secret.ttl",
                new LinkedHashSet<>(java.util.List.of("read", "modify"))));
        assertEquals("AccessRequest", doc.getString("type"));
        assertEquals("https://www.w3.org/ns/activitystreams", doc.getString("@context"));
        JsonObject access = doc.getJsonArray("access").getJsonObject(0);
        assertEquals("https://localhost:8888/user/alice#me", access.getString("assignee"));
        assertEquals("read", access.getJsonArray("action").getString(0));
        assertEquals("modify", access.getJsonArray("action").getString(1));
        assertEquals("https://s/c/secret.ttl",
                access.getJsonObject("target").getJsonArray("value").getString(0),
                "the target must be the requested resource");
    }
}
