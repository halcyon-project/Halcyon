package com.ebremer.halcyon.mcp;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.auth.BearerTokenVerifier;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Contributes Halcyon's MCP tools — and the authentication in front of them —
 * to the Spring AI MCP server.
 *
 * <p>This is an {@link AutoConfiguration} (registered in
 * {@code META-INF/spring/…AutoConfiguration.imports}), not a scanned
 * component, because the Halcyon application's component scan is rooted at
 * {@code com.ebremer.halcyon.server} — a library package like this one is
 * only found through the auto-configuration path. The {@code /mcp} endpoint
 * itself (Streamable HTTP transport, sessions, the protocol loop) comes from
 * {@code spring-ai-starter-mcp-server-webmvc}'s own auto-configuration the
 * moment this module is on the app's classpath; what this class adds is the
 * {@link ToolCallbackProvider} the server collects its tools from, and the
 * MCP-1 authentication gate in front of the endpoint.
 *
 * <p>The endpoint path is read from the same property the transport uses
 * ({@code spring.ai.mcp.server.streamable-http.mcp-endpoint}), so the guard
 * and the guarded can never disagree about where {@code /mcp} is.
 */
@AutoConfiguration
public class HalcyonMcpAutoConfiguration {

    @Bean
    public ToolCallbackProvider halcyonMcpTools() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new HalcyonInfoTools())
                .build();
    }

    /**
     * The MCP-1 authentication core. The verifier factory defers
     * {@link HalcyonSettings} and OIDC discovery to the first {@code /mcp}
     * request — a transiently unreachable Keycloak must not stop the server
     * (or this bean) from starting.
     */
    @Bean
    public McpBearerAuth mcpBearerAuth(
            @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}") String endpoint) {
        return new McpBearerAuth(() -> new BearerTokenVerifier(
                HalcyonSettings.getSettings().getProxyHostName() + endpoint));
    }

    /**
     * The gate itself, mapped to exactly the MCP endpoint's URL tree. First
     * in the filter order for those URLs: no valid token, no protocol.
     */
    @Bean
    public FilterRegistrationBean<McpBearerAuthFilter> mcpBearerAuthRegistration(McpBearerAuth auth,
            @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}") String endpoint) {
        FilterRegistrationBean<McpBearerAuthFilter> reg =
                new FilterRegistrationBean<>(new McpBearerAuthFilter(auth));
        reg.setName("halcyonMcpBearerAuth");
        reg.addUrlPatterns(endpoint, endpoint + "/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    /**
     * RFC 9728 protected-resource metadata — how a spec-compliant MCP client,
     * pointed here by the 401 challenge's {@code resource_metadata}
     * parameter, discovers the authorization server. Anonymous by design; it
     * states only what the challenge already says. (The Wicket filter must
     * ignore {@code /.well-known/} for this route to be reachable — see
     * {@code URLControl.getWicketIgnores()}.)
     */
    @Bean
    public RouterFunction<ServerResponse> mcpProtectedResourceMetadata(McpBearerAuth auth,
            @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}") String endpoint) {
        return RouterFunctions.route()
                .GET("/.well-known/oauth-protected-resource" + endpoint,
                        req -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(auth.metadataJson()))
                .build();
    }
}
