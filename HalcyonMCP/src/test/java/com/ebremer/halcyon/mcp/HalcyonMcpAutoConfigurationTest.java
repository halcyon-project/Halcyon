package com.ebremer.halcyon.mcp;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the auto-configuration contract: the tool provider bean exists without
 * any component scan (the Halcyon app scans {@code com.ebremer.halcyon.server}
 * only, so the auto-configuration path is the ONLY way this module's beans
 * reach the app context), and the skeleton exposes exactly the identification
 * tool — nothing that touches data before the P0 auth work lands.
 */
class HalcyonMcpAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HalcyonMcpAutoConfiguration.class));

    @Test
    void toolProviderIsAutoConfigured() {
        runner.run(ctx -> {
            ToolCallbackProvider provider = ctx.getBean(ToolCallbackProvider.class);
            Set<String> names = Arrays.stream(provider.getToolCallbacks())
                    .map(tc -> tc.getToolDefinition().name())
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(names.containsAll(Set.of("halcyon_version", "halcyon_whoami", "lws_storages")),
                    "the identification and storage-entry tools must be registered, got: " + names);
        });
    }

    @Test
    void versionToolAnswersWithTheServerVersion() {
        runner.run(ctx -> {
            ToolCallback tool = Arrays.stream(ctx.getBean(ToolCallbackProvider.class).getToolCallbacks())
                    .filter(tc -> "halcyon_version".equals(tc.getToolDefinition().name()))
                    .findFirst().orElseThrow();
            String out = tool.call("{}");
            assertTrue(out.contains(HalcyonSettings.VERSION),
                    "the tool must answer with the running server's version, got: " + out);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void authGateIsRegisteredForExactlyTheMcpEndpoint() {
        // MCP-1: the bearer filter must guard the endpoint's whole URL tree,
        // first in the filter order — and nothing else.
        runner.run(ctx -> {
            FilterRegistrationBean<McpBearerAuthFilter> reg =
                    ctx.getBean(FilterRegistrationBean.class);
            assertEquals(Set.of("/mcp", "/mcp/*"), Set.copyOf(reg.getUrlPatterns()),
                    "the guard and the guarded endpoint must agree on the URL tree");
            assertEquals(Ordered.HIGHEST_PRECEDENCE, reg.getOrder(),
                    "authentication runs before anything else on /mcp");
            assertNotNull(ctx.getBean(McpBearerAuth.class));
        });
    }

    @Test
    void resourcesAndPromptsAreRegistered() {
        // MCP-15: the guide resource and the two workflow prompts must be
        // contributed as specification-list beans the MCP server collects.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(McpResourcesAndPrompts.class))
                .run(ctx -> {
                    var resources = (List<?>) ctx.getBean("halcyonMcpResources");
                    var prompts = (List<?>) ctx.getBean("halcyonMcpPrompts");
                    var completions = (List<?>) ctx.getBean("halcyonMcpCompletions");
                    assertEquals(1, resources.size(), "the guide resource");
                    assertEquals(2, prompts.size(), "explore_slides and request_access");
                    assertEquals(1, completions.size(), "the request_access completion");
                });
    }

    @Test
    void protectedResourceMetadataRouteIsPublished() {
        // RFC 9728: the 401 challenge points clients at this route; it must
        // exist (and is anonymous by design — it only names the auth server).
        runner.run(ctx -> assertTrue(ctx.containsBean("mcpProtectedResourceMetadata"),
                "the oauth-protected-resource metadata route must be published"));
    }

    @Test
    void metadataUrlInsertsWellKnownBetweenAuthorityAndPath() {
        McpBearerAuth auth = new McpBearerAuth(() -> {
            throw new IllegalStateException("must not discover in this test");
        }) {
            @Override
            public String resource() {
                return "https://localhost:8888/mcp";
            }
        };
        assertEquals("https://localhost:8888/.well-known/oauth-protected-resource/mcp",
                auth.metadataUrl(),
                "RFC 9728 path-inserted form: well-known between authority and resource path");
    }

    @Test
    void callerAwareTransportProviderReplacesTheStockOne() {
        // MCP-2: our provider (with the caller context extractor) must exist
        // under the same conditions as Spring AI's own — whose bean is
        // @ConditionalOnMissingBean and therefore backs off to this one.
        runner.withPropertyValues("spring.ai.mcp.server.protocol=STREAMABLE")
                .run(ctx -> assertNotNull(
                        ctx.getBean(org.springframework.ai.mcp.server.webmvc.transport
                                .WebMvcStreamableServerTransportProvider.class),
                        "the caller-aware streamable transport provider must be auto-configured"));
    }

    @Test
    void whoamiAnswersTheTransportVerifiedCallerAndRefusesWithoutOne() {
        runner.run(ctx -> {
            ToolCallback whoami = Arrays.stream(ctx.getBean(ToolCallbackProvider.class).getToolCallbacks())
                    .filter(tc -> "halcyon_whoami".equals(tc.getToolDefinition().name()))
                    .findFirst().orElseThrow();

            var exchange = org.mockito.Mockito.mock(
                    io.modelcontextprotocol.server.McpSyncServerExchange.class);
            org.mockito.Mockito.when(exchange.transportContext()).thenReturn(
                    io.modelcontextprotocol.common.McpTransportContext.create(java.util.Map.of(
                            McpBearerAuthFilter.CALLER_ATTRIBUTE,
                            new McpCaller(new com.ebremer.lws.auth.AgentContext(
                                    "https://localhost:8888/user/alice#me", "cli", "iss",
                                    List.of()), "alice-token"))));
            String out = whoami.call("{}", new org.springframework.ai.chat.model.ToolContext(
                    java.util.Map.of(org.springframework.ai.mcp.McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY,
                            exchange)));
            assertTrue(out.contains("alice#me"),
                    "whoami must echo the transport-verified caller, got: " + out);

            assertThrows(Exception.class,
                    () -> whoami.call("{}", new org.springframework.ai.chat.model.ToolContext(java.util.Map.of())),
                    "without a verified caller the tool must refuse");
        });
    }
}
