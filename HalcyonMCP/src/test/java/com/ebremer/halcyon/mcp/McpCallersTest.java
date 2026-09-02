package com.ebremer.halcyon.mcp;

import com.ebremer.lws.auth.AgentContext;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MCP-2's rule, pinned mutation-style: {@link McpCallers#require} answers the
 * verified caller carried by the transport context, and REFUSES everything
 * else — no context, no exchange, an empty context, an unauthenticated
 * placeholder. Weakening any branch to a fallback identity must fail one of
 * these.
 */
class McpCallersTest {

    private static final AgentContext ALICE = new AgentContext(
            "https://localhost:8888/user/alice#me", "some-mcp-client",
            "https://localhost:8888/auth/realms/Halcyon", List.of());
    private static final McpCaller ALICE_CALLER = new McpCaller(ALICE, "alice-token");

    private static ToolContext contextCarrying(McpTransportContext transport) {
        McpSyncServerExchange exchange = Mockito.mock(McpSyncServerExchange.class);
        Mockito.when(exchange.transportContext()).thenReturn(transport);
        return new ToolContext(Map.of(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY, exchange));
    }

    @Test
    void verifiedCallerIsAnswered() {
        ToolContext ctx = contextCarrying(McpTransportContext.create(
                Map.of(McpBearerAuthFilter.CALLER_ATTRIBUTE, ALICE_CALLER)));
        assertEquals(ALICE_CALLER, McpCallers.require(ctx));
        assertEquals("alice-token", McpCallers.require(ctx).token(),
                "the caller's own token must be carried, not just their identity");
    }

    @Test
    void noToolContextIsARefusal() {
        assertThrows(IllegalStateException.class, () -> McpCallers.require(null));
    }

    @Test
    void noExchangeIsARefusal() {
        assertThrows(IllegalStateException.class,
                () -> McpCallers.require(new ToolContext(Map.of())));
    }

    @Test
    void emptyTransportContextIsARefusal() {
        assertThrows(IllegalStateException.class,
                () -> McpCallers.require(contextCarrying(McpTransportContext.EMPTY)));
    }

    @Test
    void unauthenticatedAgentIsARefusalNeverAFallback() {
        ToolContext ctx = contextCarrying(McpTransportContext.create(
                Map.of(McpBearerAuthFilter.CALLER_ATTRIBUTE,
                        new McpCaller(AgentContext.PUBLIC, null))));
        assertThrows(IllegalStateException.class, () -> McpCallers.require(ctx),
                "the PUBLIC placeholder must never pass as a caller");
    }
}
