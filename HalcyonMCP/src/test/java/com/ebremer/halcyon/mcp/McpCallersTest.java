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

    private static ToolContext contextCarrying(McpTransportContext transport) {
        McpSyncServerExchange exchange = Mockito.mock(McpSyncServerExchange.class);
        Mockito.when(exchange.transportContext()).thenReturn(transport);
        return new ToolContext(Map.of(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY, exchange));
    }

    @Test
    void verifiedCallerIsAnswered() {
        ToolContext ctx = contextCarrying(McpTransportContext.create(
                Map.of(McpBearerAuthFilter.AGENT_ATTRIBUTE, ALICE)));
        assertEquals(ALICE, McpCallers.require(ctx));
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
                Map.of(McpBearerAuthFilter.AGENT_ATTRIBUTE, AgentContext.PUBLIC)));
        assertThrows(IllegalStateException.class, () -> McpCallers.require(ctx),
                "the PUBLIC placeholder must never pass as a caller");
    }
}
