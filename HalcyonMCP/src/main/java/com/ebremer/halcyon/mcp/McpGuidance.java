package com.ebremer.halcyon.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * MCP-15's content, kept free of the SDK types so it is unit-testable on its
 * own: the guide text (a bundled classpath resource) and the rendered
 * canonical-workflow prompts. {@link McpResourcesAndPrompts} wraps these in
 * the SDK's resource/prompt specifications.
 */
final class McpGuidance {

    private McpGuidance() {
    }

    /** URI of the "how to use this server" guide resource. */
    static final String GUIDE_URI = "halcyon:///guide";

    private static final String GUIDE_RESOURCE = "mcp/using-halcyon.md";

    /** The bundled agent guide, read from the classpath. */
    static String guide() {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(GUIDE_RESOURCE)) {
            if (in == null) {
                return "The Halcyon MCP guide resource is missing from the build.";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "The Halcyon MCP guide could not be read: " + e.getMessage();
        }
    }

    /**
     * The {@code explore_slides} prompt body: a concrete plan a model can run.
     * {@code focus} narrows it when supplied (a stain, a tissue, a case), else
     * the plan is general.
     */
    static String exploreSlides(String focus) {
        String scope = focus == null || focus.isBlank()
                ? "the whole-slide images I can access"
                : "whole-slide images related to \"" + focus.trim() + "\"";
        return """
            Explore %s in this Halcyon server.

            1. Call `find_slides` to locate them (each match names its image URI \
            and IIIF endpoint).
            2. For a few of the most relevant, call `iiif_info` for dimensions and \
            `iiif_thumbnail` for a small preview.
            3. Call `list_stacks` to see which have Zephyr annotation stacks, and \
            `lws_read` a stack's Turtle to summarise its annotations.
            4. Report what you found: how many slides, their sizes, and which \
            carry annotations. If any step returns 403, say so plainly rather \
            than guessing.""".formatted(scope);
    }

    /**
     * The {@code request_access} prompt body: the protocol-correct response to
     * a refusal, for a specific resource and set of actions.
     */
    static String requestAccess(String resource, String actions) {
        String res = resource == null || resource.isBlank()
                ? "the resource you were refused" : resource;
        String acts = actions == null || actions.isBlank() ? "read" : actions.trim();
        return """
            You were refused access to %s. File a request for it.

            1. Call `lws_request_access` with resource=%s and actions=%s.
            2. Tell me the request was recorded (and its location if returned), \
            and that a storage controller must approve it before it grants \
            anything — it does not grant access by itself.""".formatted(res, res, acts);
    }

    /** Prompt argument names, shared by the spec and the renderers. */
    static String arg(Map<String, Object> arguments, String name) {
        Object v = arguments == null ? null : arguments.get(name);
        return v == null ? null : String.valueOf(v);
    }

    /** The actions an access request may name (mirrors {@link LwsAccessTools}). */
    static final java.util.List<String> ACTIONS =
            java.util.List.of("read", "modify", "create", "delete");

    /**
     * MCP-16 completion for the {@code request_access} prompt's arguments —
     * SDK-free so it is unit-testable. {@code resource} completes from the
     * configured storage roots (a place to start a URI); {@code actions}
     * completes the last comma-separated token from the fixed action set. Any
     * other argument yields nothing.
     */
    static java.util.List<String> complete(String argName, String value,
            java.util.List<String> storageRoots) {
        String v = value == null ? "" : value;
        if ("resource".equals(argName)) {
            return storageRoots.stream().filter(r -> r.startsWith(v)).toList();
        }
        if ("actions".equals(argName)) {
            int comma = v.lastIndexOf(',');
            String prefix = v.substring(comma + 1).trim().toLowerCase(java.util.Locale.ROOT);
            return ACTIONS.stream().filter(a -> a.startsWith(prefix)).toList();
        }
        return java.util.List.of();
    }
}
