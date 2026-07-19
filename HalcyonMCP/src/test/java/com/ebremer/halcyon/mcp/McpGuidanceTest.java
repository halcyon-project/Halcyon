package com.ebremer.halcyon.mcp;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP-15: the guide is bundled and the prompt renderers produce concrete,
 * argument-aware plans that name the real tools (a prompt that named a
 * non-existent tool would send a model down a dead end).
 */
class McpGuidanceTest {

    @Test
    void guideIsBundledAndDescribesTheTools() {
        String g = McpGuidance.guide();
        assertFalse(g.startsWith("The Halcyon MCP guide"), "the guide resource must be on the classpath");
        assertTrue(g.contains("lws_storages") && g.contains("find_slides") && g.contains("lws_put"),
                "the guide must actually orient an agent to the tools");
    }

    @Test
    void exploreSlidesPromptIsGeneralOrFocusedAndNamesRealTools() {
        String general = McpGuidance.exploreSlides(null);
        assertTrue(general.contains("find_slides") && general.contains("iiif_info")
                && general.contains("list_stacks"), "the plan must name the real tools");
        assertTrue(general.contains("whole-slide images I can access"));

        String focused = McpGuidance.exploreSlides("HER2 breast");
        assertTrue(focused.contains("HER2 breast"), "the focus must narrow the plan");
    }

    @Test
    void requestAccessPromptCarriesResourceAndActions() {
        String p = McpGuidance.requestAccess("https://s/c/secret.ttl", "read,modify");
        assertTrue(p.contains("lws_request_access"));
        assertTrue(p.contains("https://s/c/secret.ttl"));
        assertTrue(p.contains("read,modify"));
        // Defaulting when omitted.
        assertTrue(McpGuidance.requestAccess("https://s/c/x.ttl", null).contains("actions=read"));
    }

    @Test
    void argHelperIsNullSafe() {
        assertEquals("v", McpGuidance.arg(Map.of("k", "v"), "k"));
        org.junit.jupiter.api.Assertions.assertNull(McpGuidance.arg(null, "k"));
        org.junit.jupiter.api.Assertions.assertNull(McpGuidance.arg(Map.of(), "k"));
    }

    @Test
    void completionSuggestsStorageRootsForResourceByPrefix() {
        var roots = java.util.List.of("https://a.example/store/", "https://b.example/store/");
        assertEquals(roots, McpGuidance.complete("resource", "https://", roots));
        assertEquals(java.util.List.of("https://a.example/store/"),
                McpGuidance.complete("resource", "https://a", roots));
        assertTrue(McpGuidance.complete("resource", "ftp://", roots).isEmpty());
    }

    @Test
    void completionSuggestsActionsForTheLastToken() {
        var none = java.util.List.<String>of();
        assertEquals(McpGuidance.ACTIONS, McpGuidance.complete("actions", "", none));
        assertEquals(java.util.List.of("read"), McpGuidance.complete("actions", "re", none));
        // Completes the token AFTER the last comma, not the whole value.
        assertEquals(java.util.List.of("modify"), McpGuidance.complete("actions", "read, mo", none));
        assertTrue(McpGuidance.complete("focus", "anything", none).isEmpty(),
                "an argument with no completion yields nothing");
    }
}
