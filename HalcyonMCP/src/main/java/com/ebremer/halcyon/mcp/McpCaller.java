package com.ebremer.halcyon.mcp;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.client.LwsClient;

/**
 * One authenticated MCP caller: their verified {@link AgentContext} (WebID,
 * client, issuer — the identity ACP matches on) together with the raw bearer
 * token their request carried. Both are needed and they are not the same
 * thing: the agent is who a tool <em>is acting as</em>, the token is what a
 * tool <em>presents</em> to the storage so the storage makes that same ACP
 * decision itself.
 *
 * <p>This is the object MCP-2 carries on the transport context and MCP-1's
 * filter mints. The token stays out of {@link AgentContext} on purpose — that
 * record is passed around for identity matching and must not become a place a
 * live credential leaks — so it rides here instead, reaching only the tools
 * that call {@link #lwsClient()}.
 *
 * @param agent the verified identity, always authenticated (the filter
 *              refuses anonymous before minting a caller)
 * @param token the caller's own access token, valid for the local origin
 */
public record McpCaller(AgentContext agent, String token) {

    /**
     * An LWS client bound to THIS caller's token — the sanctioned way a tool
     * touches a storage. The token is attached only to the local origin (see
     * {@link LwsClient}); a request this client cannot make is a request the
     * caller cannot make, which is the whole point.
     */
    public LwsClient lwsClient() {
        return new LwsClient(token, HalcyonSettings.getSettings().getProxyHostName());
    }

    /** The caller's WebID — how a tool names them in output or an audit line. */
    public String webId() {
        return agent.webId();
    }
}
