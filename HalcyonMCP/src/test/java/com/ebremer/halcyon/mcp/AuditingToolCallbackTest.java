package com.ebremer.halcyon.mcp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP-17: the decorator is a pass-through that records — it returns the
 * delegate's result unchanged, rethrows a refusal (never swallows it), and
 * increments a per-tool/outcome timer when a registry is present.
 */
class AuditingToolCallbackTest {

    private static ToolCallback fake(String name, java.util.function.Supplier<String> body) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return DefaultToolDefinition.builder().name(name).description(name)
                        .inputSchema("{}").build();
            }

            @Override
            public String call(String toolInput) {
                return body.get();
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return body.get();
            }
        };
    }

    @Test
    void passesResultThroughAndCountsSuccess() {
        MeterRegistry reg = new SimpleMeterRegistry();
        ToolCallback t = new AuditingToolCallback(fake("lws_list", () -> "RESULT"), reg);
        assertEquals("RESULT", t.call("{}", new ToolContext(java.util.Map.of())));
        assertEquals(1, (long) reg.get("halcyon.mcp.tool.calls")
                .tag("tool", "lws_list").tag("outcome", "ok").timer().count());
    }

    @Test
    void rethrowsRefusalAndCountsError() {
        MeterRegistry reg = new SimpleMeterRegistry();
        ToolCallback t = new AuditingToolCallback(
                fake("sparql_query", () -> { throw new IllegalStateException("no caller"); }), reg);
        assertThrows(IllegalStateException.class,
                () -> t.call("{}", new ToolContext(java.util.Map.of())),
                "the decorator must never swallow a tool's refusal");
        assertEquals(1, (long) reg.get("halcyon.mcp.tool.calls")
                .tag("tool", "sparql_query").tag("outcome", "error").timer().count());
    }

    @Test
    void worksWithoutAMeterRegistry() {
        ToolCallback t = new AuditingToolCallback(fake("halcyon_version", () -> "v"), null);
        assertTrue(t.call("{}", new ToolContext(java.util.Map.of())).equals("v"),
                "metrics are optional; auditing still works with no registry");
    }
}
