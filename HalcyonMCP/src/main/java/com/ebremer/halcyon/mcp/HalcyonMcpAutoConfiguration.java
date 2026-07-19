package com.ebremer.halcyon.mcp;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.auth.BearerTokenVerifier;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import java.util.Map;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStdioDisabledCondition;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.autoconfigure.McpServerStreamableHttpWebMvcAutoConfiguration;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.json.JsonMapper;

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
 *
 * <p>Runs {@code before} the Spring AI streamable-HTTP auto-configuration
 * because MCP-2 replaces its transport-provider bean (which is
 * {@code @ConditionalOnMissingBean}) with an identical one that additionally
 * carries a context extractor — the SDK's supported way to hand the
 * filter-verified caller to tool invocations wherever they execute.
 */
@AutoConfiguration(before = McpServerStreamableHttpWebMvcAutoConfiguration.class)
@EnableConfigurationProperties(McpServerStreamableHttpProperties.class)
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

    /**
     * MCP-2: the streamable transport, rebuilt exactly as Spring AI's own
     * auto-configuration builds it (same properties, same JSON mapper, same
     * conditions — that bean is {@code @ConditionalOnMissingBean} and backs
     * off to this one) plus the one thing it lacks: a
     * {@link McpTransportContextExtractor} that copies the filter-verified
     * {@link com.ebremer.lws.auth.AgentContext} from the HTTP request into
     * the SDK's transport context. That context rides the exchange to the
     * tool invocation — which the server may run OFF the servlet thread, so
     * a thread-local would be luck; this is the supported carrier. Tools
     * read it through {@link McpCallers#require} and refuse without it.
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional({McpServerStdioDisabledCondition.class,
        McpServerAutoConfiguration.EnabledStreamableServerCondition.class})
    static class CallerAwareTransport {

        @Bean
        public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
                ObjectProvider<JsonMapper> jsonMapper,
                McpServerStreamableHttpProperties properties) {
            McpTransportContextExtractor<ServerRequest> caller = request -> {
                Object agent = request.servletRequest()
                        .getAttribute(McpBearerAuthFilter.AGENT_ATTRIBUTE);
                return agent == null ? McpTransportContext.EMPTY
                        : McpTransportContext.create(
                                Map.of(McpBearerAuthFilter.AGENT_ATTRIBUTE, agent));
            };
            WebMvcStreamableServerTransportProvider.Builder builder =
                    WebMvcStreamableServerTransportProvider.builder()
                            .jsonMapper(new JacksonMcpJsonMapper(
                                    jsonMapper.getIfAvailable(() -> JsonMapper.builder().build())))
                            .mcpEndpoint(properties.getMcpEndpoint())
                            .disallowDelete(properties.isDisallowDelete())
                            .contextExtractor(caller);
            if (properties.getKeepAliveInterval() != null) {
                builder.keepAliveInterval(properties.getKeepAliveInterval());
            }
            return builder.build();
        }
    }
}
