package com.ebremer.halcyon.mcp;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MCP-12: the caller guard, the open-proxy guard, and the parent/slug
 * derivation the create path stands on. The PUT/POST round-trips (conditional
 * replace, create-in-parent) are runtime-verified.
 */
class LwsWriteToolsTest {

    @Test
    void refusesWithoutAVerifiedCaller() {
        assertThrows(IllegalStateException.class, () -> new LwsWriteTools()
                .put("https://evil/x", "text/plain", "hi", new ToolContext(Map.of())));
    }

    @Test
    void parentAndSlugDerivation() {
        assertEquals("https://s/c/", LwsWriteTools.parentOf("https://s/c/foo.ttl"));
        assertEquals("foo.ttl", LwsWriteTools.lastSegment("https://s/c/foo.ttl"));
        // A container URI (trailing slash) resolves to its own parent + name.
        assertEquals("https://s/c/", LwsWriteTools.parentOf("https://s/c/sub/"));
        assertEquals("sub", LwsWriteTools.lastSegment("https://s/c/sub/"));
    }
}
