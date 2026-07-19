package com.ebremer.halcyon.mcp;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
