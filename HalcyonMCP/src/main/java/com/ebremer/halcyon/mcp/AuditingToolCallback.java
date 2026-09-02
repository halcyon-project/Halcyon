package com.ebremer.halcyon.mcp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * MCP-17: wraps a tool so every call leaves a trace — an audit line naming the
 * <em>principal</em>, the tool, and the outcome, plus (when a
 * {@link MeterRegistry} is present) a timer tagged by tool and outcome. The
 * storage already logs its ACP decisions; this is the MCP-side record of who
 * asked what.
 *
 * <p>It is a pass-through: the wrapped tool's behaviour is unchanged, and a
 * tool that refuses (no verified caller, a guardrail trip) is logged as
 * {@code error} with the exception rethrown — the decorator never swallows a
 * failure or changes a result. The principal is read non-throwingly
 * ({@link McpCallers#peek}); the tool's own {@code require} remains the gate.
 */
public final class AuditingToolCallback implements ToolCallback {

    private static final Logger audit = LoggerFactory.getLogger("com.ebremer.halcyon.mcp.audit");

    private final ToolCallback delegate;
    private final MeterRegistry meters;

    public AuditingToolCallback(ToolCallback delegate, MeterRegistry meters) {
        this.delegate = delegate;
        this.meters = meters;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String tool = getToolDefinition().name();
        McpCaller caller = McpCallers.peek(toolContext);
        String who = caller == null || caller.webId() == null ? "unknown" : caller.webId();
        long start = System.nanoTime();
        String outcome = "ok";
        try {
            return delegate.call(toolInput, toolContext);
        } catch (RuntimeException e) {
            outcome = "error";
            audit.warn("tool={} caller={} outcome=error: {}", tool, who, e.toString());
            throw e;
        } finally {
            long nanos = System.nanoTime() - start;
            if ("ok".equals(outcome)) {
                audit.info("tool={} caller={} outcome=ok ms={}", tool, who,
                        TimeUnit.NANOSECONDS.toMillis(nanos));
            }
            if (meters != null) {
                Timer.builder("halcyon.mcp.tool.calls")
                        .tag("tool", tool).tag("outcome", outcome)
                        .register(meters)
                        .record(nanos, TimeUnit.NANOSECONDS);
            }
        }
    }
}
