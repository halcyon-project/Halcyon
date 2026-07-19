package com.ebremer.halcyon.mcp;

import com.ebremer.lws.auth.AgentContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;

/**
 * MCP-2: how a tool learns WHO is calling — and the rule that it must refuse
 * when it cannot.
 *
 * <p>The chain of custody: {@code McpBearerAuthFilter} verifies the bearer
 * token and stashes the {@link AgentContext} on the HTTP request; the
 * transport provider's context extractor (see
 * {@code HalcyonMcpAutoConfiguration}) copies it into the SDK's
 * {@link io.modelcontextprotocol.common.McpTransportContext}, which rides the
 * protocol exchange to wherever the tool actually executes. That last hop is
 * the point: the MCP server may run a tool handler off the servlet thread, so
 * a thread-local (Spring's request holder included) would be luck, not
 * design. The transport context is the SDK's supported carrier.
 *
 * <p>{@link #require} is the only sanctioned way for a tool to identify its
 * caller. No verified agent — no exchange, no context, an unauthenticated
 * placeholder — is a refusal, never a fallback to some ambient or shared
 * identity. A tool that cannot name its caller must not act.
 */
public final class McpCallers {

    private McpCallers() {
    }

    /**
     * The verified caller of the current tool invocation.
     *
     * @throws IllegalStateException when there is none — the tool call did
     *                               not arrive through the authenticated MCP
     *                               transport
     */
    public static AgentContext require(ToolContext toolContext) {
        AgentContext agent = toolContext == null ? null
                : McpToolUtils.getMcpExchange(toolContext)
                        .map(McpSyncServerExchange::transportContext)
                        .map(t -> t.get(McpBearerAuthFilter.AGENT_ATTRIBUTE))
                        .filter(AgentContext.class::isInstance)
                        .map(AgentContext.class::cast)
                        .orElse(null);
        if (agent == null || !agent.isAuthenticated()) {
            throw new IllegalStateException(
                    "no verified caller for this tool call - refusing");
        }
        return agent;
    }
}
