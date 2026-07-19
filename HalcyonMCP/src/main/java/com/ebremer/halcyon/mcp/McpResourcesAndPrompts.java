package com.ebremer.halcyon.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * MCP-15: the server's <em>resources</em> and <em>prompts</em>.
 *
 * <p>A resource is content a client can read on its own — here, the guide to
 * using this server ({@code halcyon:///guide}), so an agent can orient without
 * being told how each tool works. Prompts are ready-made plans for the
 * canonical workflows: {@code explore_slides} (find imagery → info → thumbnail
 * → stacks) and {@code request_access} (the protocol-correct response to a
 * 403). The prompt bodies are rendered by {@link McpGuidance}, kept SDK-free
 * and unit-tested; this class only wraps them in the SDK specifications the
 * Spring AI MCP server collects.
 *
 * <p>Both are read-only and disclose nothing access-sensitive — the guide is
 * static text and the prompts are instructions, not data — so neither needs
 * the caller check the tools carry.
 */
@AutoConfiguration
public class McpResourcesAndPrompts {

    @Bean
    public List<SyncResourceSpecification> halcyonMcpResources() {
        McpSchema.Resource guide = McpSchema.Resource.builder()
                .uri(McpGuidance.GUIDE_URI)
                .name("using-halcyon")
                .title("Using the Halcyon MCP server")
                .description("How this server's tools work and the one rule they all follow.")
                .mimeType("text/markdown")
                .build();

        SyncResourceSpecification guideSpec = new SyncResourceSpecification(guide,
                (exchange, request) -> new McpSchema.ReadResourceResult(List.of(
                        new McpSchema.TextResourceContents(request.uri(), "text/markdown",
                                McpGuidance.guide()))));

        return List.of(guideSpec);
    }

    @Bean
    public List<SyncPromptSpecification> halcyonMcpPrompts() {
        McpSchema.Prompt exploreSlides = new McpSchema.Prompt("explore_slides",
                "Find and summarise whole-slide images (optionally on a focus) and their "
                        + "annotation stacks.",
                List.of(new McpSchema.PromptArgument("focus",
                        "Optional subject to narrow the search (a stain, tissue, or case).",
                        false)));

        McpSchema.Prompt requestAccess = new McpSchema.Prompt("request_access",
                "File an access request for a resource you were refused.",
                List.of(new McpSchema.PromptArgument("resource",
                        "The resource URI you were refused.", true),
                        new McpSchema.PromptArgument("actions",
                                "Comma-separated actions (read, modify, create, delete; "
                                        + "default read).", false)));

        SyncPromptSpecification exploreSpec = new SyncPromptSpecification(exploreSlides,
                (exchange, request) -> userPrompt(exploreSlides.description(),
                        McpGuidance.exploreSlides(McpGuidance.arg(request.arguments(), "focus"))));

        SyncPromptSpecification accessSpec = new SyncPromptSpecification(requestAccess,
                (exchange, request) -> userPrompt(requestAccess.description(),
                        McpGuidance.requestAccess(
                                McpGuidance.arg(request.arguments(), "resource"),
                                McpGuidance.arg(request.arguments(), "actions"))));

        return List.of(exploreSpec, accessSpec);
    }

    private static McpSchema.GetPromptResult userPrompt(String description, String body) {
        return new McpSchema.GetPromptResult(description, List.of(
                new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(body))));
    }
}
