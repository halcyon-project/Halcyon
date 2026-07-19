package com.ebremer.halcyon.mcp;

import com.ebremer.lws.auth.AgentContext;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import jakarta.json.Json;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MCP-6: {@code lws_storages} requires a verified caller and answers
 * well-formed JSON. The storage <em>content</em> depends on this host's
 * {@code settings.ttl} (none in the test working dir → an empty list), so
 * what is pinned here is the contract, not a fixture: refusal without a
 * caller, and a parseable {@code {"storages":[...]}} envelope with one.
 */
class LwsStorageToolsTest {

    private static ToolContext callerContext() {
        McpSyncServerExchange exchange = Mockito.mock(McpSyncServerExchange.class);
        Mockito.when(exchange.transportContext()).thenReturn(McpTransportContext.create(Map.of(
                McpBearerAuthFilter.CALLER_ATTRIBUTE,
                new McpCaller(new AgentContext("https://localhost:8888/user/alice#me",
                        "cli", "iss", List.of()), "tok"))));
        return new ToolContext(Map.of(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY, exchange));
    }

    @Test
    void refusesWithoutAVerifiedCaller() {
        assertThrows(IllegalStateException.class,
                () -> new LwsStorageTools().storages(new ToolContext(Map.of())));
    }

    @Test
    void answersAParseableStoragesEnvelope() {
        String out = new LwsStorageTools().storages(callerContext());
        try (var r = Json.createReader(new StringReader(out))) {
            assertNotNull(r.readObject().getJsonArray("storages"),
                    "the answer must carry a 'storages' array");
        }
    }
}
