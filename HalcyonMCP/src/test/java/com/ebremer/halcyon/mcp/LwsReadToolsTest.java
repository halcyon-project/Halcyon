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
 * MCP-8: the text-gate and the three outcomes, plus the open-proxy refusal.
 */
class LwsReadToolsTest {

    private static JsonObject parse(String s) {
        try (var r = Json.createReader(new StringReader(s))) {
            return r.readObject();
        }
    }

    @Test
    void textLikenessCoversTextStructuredAndRdf() {
        assertTrue(LwsReadTools.isTextLike("text/plain"));
        assertTrue(LwsReadTools.isTextLike("text/turtle; charset=utf-8"));
        assertTrue(LwsReadTools.isTextLike("application/json"));
        assertTrue(LwsReadTools.isTextLike("application/ld+json"));
        assertTrue(LwsReadTools.isTextLike("image/svg+xml"));
        assertTrue(LwsReadTools.isTextLike("application/sparql-query"));
        assertFalse(LwsReadTools.isTextLike("image/png"));
        assertFalse(LwsReadTools.isTextLike("application/octet-stream"));
        assertFalse(LwsReadTools.isTextLike("application/pdf"));
        assertFalse(LwsReadTools.isTextLike(null));
    }

    @Test
    void textResourceIsReturnedWithTruncationFlag() {
        JsonObject out = parse(LwsReadTools.formatRead("https://s/c/onto.ttl",
                200, "text/turtle", "@prefix ex: <https://ex/> .", true));
        assertEquals("text/turtle", out.getString("mediaType"));
        assertTrue(out.getBoolean("truncated"));
        assertTrue(out.getString("text").contains("@prefix"));
    }

    @Test
    void binaryResourceIsRefusedWithTheUriToOpen() {
        JsonObject out = parse(LwsReadTools.formatRead("https://s/c/slide.tiff",
                200, "image/tiff", "�binary�", false));
        assertTrue(out.getBoolean("binary"));
        assertFalse(out.containsKey("text"), "binary bytes must not be returned as text");
        assertEquals("https://s/c/slide.tiff", out.getString("uri"));
    }

    @Test
    void storageErrorIsRenderedVerbatim() {
        JsonObject out = parse(LwsReadTools.formatRead("https://s/c/secret.ttl",
                403, "text/plain", "Forbidden", false));
        assertEquals(403, out.getInt("status"));
        assertTrue(out.getString("error").contains("Forbidden"));
        assertFalse(out.containsKey("text"));
    }

    @Test
    void refusesAUriOutsideAnyConfiguredStorage() {
        assertThrows(IllegalStateException.class, () -> new LwsReadTools()
                .read("https://evil.example/x", new ToolContext(Map.of())));
    }
}
