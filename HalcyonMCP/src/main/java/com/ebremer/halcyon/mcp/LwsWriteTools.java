package com.ebremer.halcyon.mcp;

import com.ebremer.lws.client.LwsClient;
import jakarta.json.Json;
import java.nio.charset.StandardCharsets;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * MCP-12: {@code lws_put} — create or replace a text resource, as the caller,
 * with the conditional-write discipline the storage panels use. Whether the
 * caller <em>may</em> write is the storage's ACP decision on the PUT/POST
 * itself; a refusal (403) is rendered verbatim.
 *
 * <p>Two shapes, because the storage has two:
 * <ul>
 *   <li><strong>Replace</strong> an existing resource: the entity tag is read
 *       first ({@code HEAD}) and the {@code PUT} carries it as {@code If-Match}
 *       — a genuine compare-and-swap. If the resource changed underneath,
 *       the storage answers 412 (or 428 for an unconditional write); either
 *       is surfaced verbatim, never retried blind. This is the case that can
 *       lose data, so it is the case that is protected.</li>
 *   <li><strong>Create</strong> a resource that does not exist: a {@code POST}
 *       to the parent container with the target's name as {@code Slug} — the
 *       storage's own create path (its default {@code PUT} is replace-only),
 *       and the server's naming is authoritative, so the answer carries the
 *       {@code Location} where the resource actually landed.</li>
 * </ul>
 *
 * <p>The body is UTF-8 text from the tool argument, capped at
 * {@link Guardrails#MAX_WRITE_BYTES}; and the target must live inside a
 * configured storage (no open proxy).
 */
public class LwsWriteTools {

    @Tool(name = "lws_put",
            description = "Create or replace a TEXT resource in a Linked Web Storage, as the "
                    + "calling user (the storage authorizes the write; a refusal is returned "
                    + "verbatim). Replacing an existing resource is a safe conditional write - "
                    + "if it changed underneath you the call reports a conflict rather than "
                    + "overwriting. Provide the resource URI, a content type, and the text.")
    public String put(
            @ToolParam(description = "The resource URI to create or replace.")
            String resource,
            @ToolParam(description = "The media type to store it as, e.g. text/turtle, "
                    + "application/json, text/plain.")
            String contentType,
            @ToolParam(description = "The resource content, as UTF-8 text (max 1 MB).")
            String content,
            ToolContext toolContext) {
        McpCaller caller = McpCallers.require(toolContext);
        LwsSupport.requireWithinStorage(resource);

        byte[] body = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        if (body.length > Guardrails.MAX_WRITE_BYTES) {
            return err(resource, "content exceeds " + Guardrails.MAX_WRITE_BYTES
                    + " bytes; use a purpose-built ingest path for large uploads");
        }
        String type = contentType == null || contentType.isBlank() ? "text/plain" : contentType.trim();

        LwsClient client = caller.lwsClient();
        String etag = client.etag(resource);
        if (etag != null) {
            // Exists → conditional replace. If-Match is the lost-update guard.
            LwsClient.Result r = client.put(resource, type, body, etag);
            if (r.ok()) {
                return Json.createObjectBuilder()
                        .add("resource", resource).add("action", "replaced")
                        .add("status", r.status()).build().toString();
            }
            return err(resource, r.status(), LwsSupport.problem(r.status(), r.body()));
        }

        // Absent → create in the parent container (the storage's create path).
        String parent = parentOf(resource);
        String slug = lastSegment(resource);
        LwsClient.Result r = client.post(parent, slug, type, body, false);
        if (r.status() == 201) {
            return Json.createObjectBuilder()
                    .add("resource", resource).add("action", "created")
                    .add("location", r.location() == null ? "" : r.location())
                    .add("status", 201).build().toString();
        }
        return err(resource, r.status(), LwsSupport.problem(r.status(), r.body()));
    }

    /** The parent container of a member URI (containers end with a slash). */
    static String parentOf(String uri) {
        String s = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(0, i + 1) : uri;
    }

    /** The last path segment — the create Slug. */
    static String lastSegment(String uri) {
        String s = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        int i = s.lastIndexOf('/');
        return i >= 0 && i < s.length() - 1 ? s.substring(i + 1) : s;
    }

    private static String err(String resource, String message) {
        return Json.createObjectBuilder()
                .add("resource", resource).add("error", message).build().toString();
    }

    private static String err(String resource, int status, String message) {
        return Json.createObjectBuilder()
                .add("resource", resource).add("status", status)
                .add("error", message).build().toString();
    }
}
