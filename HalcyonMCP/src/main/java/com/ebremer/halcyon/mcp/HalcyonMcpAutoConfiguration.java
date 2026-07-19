package com.ebremer.halcyon.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Contributes Halcyon's MCP tools to the Spring AI MCP server.
 *
 * <p>This is an {@link AutoConfiguration} (registered in
 * {@code META-INF/spring/…AutoConfiguration.imports}), not a scanned
 * component, because the Halcyon application's component scan is rooted at
 * {@code com.ebremer.halcyon.server} — a library package like this one is
 * only found through the auto-configuration path. The {@code /mcp} endpoint
 * itself (Streamable HTTP transport, sessions, the protocol loop) comes from
 * {@code spring-ai-starter-mcp-server-webmvc}'s own auto-configuration the
 * moment this module is on the app's classpath; what this class adds is the
 * {@link ToolCallbackProvider} that server collects its tools from.
 */
@AutoConfiguration
public class HalcyonMcpAutoConfiguration {

    @Bean
    public ToolCallbackProvider halcyonMcpTools() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new HalcyonInfoTools())
                .build();
    }
}
