package com.ebremer.halcyon.mcp;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.auth.AgentContext;
import jakarta.json.Json;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

/**
 * The identification tools: what this server is, and who the caller is.
 * Neither touches a dataset — {@code halcyon_version} reads static constants,
 * and {@code halcyon_whoami} echoes only the identity the caller's own token
 * proved. Every tool that touches actual Halcyon data (LWS listings, SPARQL,
 * imagery) is specified in {@code TODO.md} P1 and inherits the P0 rules:
 * the caller's identity via {@link McpCallers#require}, bounded reads, no
 * privileged path.
 */
public class HalcyonInfoTools {

    @Tool(name = "halcyon_version",
            description = "The Halcyon server software name and version.")
    public String version() {
        return HalcyonSettings.HALCYONSOFTWARE;
    }

    /**
     * MCP-2's proof-of-plumbing (and a genuinely useful tool): the caller as
     * this server verified them. If the identity cannot be resolved from the
     * authenticated transport, {@link McpCallers#require} refuses — there is
     * no anonymous or ambient answer.
     */
    @Tool(name = "halcyon_whoami",
            description = "The authenticated caller as this server verified them: "
                    + "WebID, OAuth client, and token issuer.")
    public String whoami(ToolContext toolContext) {
        AgentContext agent = McpCallers.require(toolContext);
        return Json.createObjectBuilder()
                .add("webid", agent.webId())
                .add("client", agent.clientId() == null ? "" : agent.clientId())
                .add("issuer", agent.issuer() == null ? "" : agent.issuer())
                .build().toString();
    }
}
