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
            List<String> names = Arrays.stream(provider.getToolCallbacks())
                    .map(tc -> tc.getToolDefinition().name())
                    .toList();
            assertEquals(List.of("halcyon_version"), names,
                    "exactly the identification tool — data tools are gated on P0 auth (TODO.md)");
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
}
