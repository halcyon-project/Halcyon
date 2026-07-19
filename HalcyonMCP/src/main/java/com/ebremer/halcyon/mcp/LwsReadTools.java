package com.ebremer.halcyon.mcp;

import com.ebremer.lws.client.LwsClient;
import jakarta.json.Json;
import java.util.Locale;
import java.util.Set;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * MCP-8: {@code lws_read} — the bytes of one <em>text-like</em> resource,
 * fetched over the LWS API as the caller and <strong>bounded</strong> to
 * {@link Guardrails#MAX_TEXT_BYTES} (the transfer aborts at the cap; a huge
 * file is never pulled whole through the JVM), with a truncation flag when it
 * was cut.
 *
 * <p>Two refusals, both deliberate. A resource whose media type is not
 * text-like is not returned as text — the tool reports it is binary and hands
 * back the resource URI to open directly (the storage will still make the ACP
 * decision there); base64-inlining arbitrary blobs into a model's context is
 * a job for a purpose-built tool (imagery has {@code iiif_thumbnail}), not
 * this one. And a URI outside a configured storage is refused outright (no
 * open proxy). What the caller may read is, as always, ACP's call: a 403 is
 * rendered verbatim.
 */
public class LwsReadTools {

    /**
     * Media types served as text. Beyond {@code text/*} and the
     * structured-syntax suffixes, an allowlist of the RDF/query/data types
     * whose registration is {@code application/*} but whose content is text.
     */
    private static final Set<String> TEXT_APPLICATION = Set.of(
            "application/json", "application/xml", "application/yaml", "application/x-yaml",
            "application/ld+json", "application/n-triples", "application/n-quads",
            "application/trig", "application/turtle", "application/x-turtle",
            "application/sparql-query", "application/sparql-update",
            "application/sql", "application/graphql", "application/javascript",
            "application/ecmascript", "application/xhtml+xml", "application/rdf+xml",
            "application/x-sh", "application/x-httpd-php");

    @Tool(name = "lws_read",
            description = "Read the text of one Linked Web Storage resource (e.g. Turtle, JSON, "
                    + "XML, SPARQL, source, plain text), as the calling user's access allows, "
                    + "bounded to 256 kB with a truncation flag. Binary resources are not "
                    + "returned - the tool reports the media type and the URI to open directly.")
    public String read(
            @ToolParam(description = "The resource URI to read (from lws_list).")
            String resource,
            ToolContext toolContext) {
        McpCaller caller = McpCallers.require(toolContext);
        LwsSupport.requireWithinStorage(resource);

        LwsClient.Preview p = caller.lwsClient().preview(resource, Guardrails.MAX_TEXT_BYTES);
        return formatRead(resource, p.status(), p.contentType(), p.text(), p.truncated());
    }

    /**
     * Shape the bounded preview into the tool's answer — split from the fetch
     * so the text-gate and the three outcomes (ok / binary / storage error)
     * are testable without a live storage.
     */
    static String formatRead(String uri, int status, String contentType,
            String text, boolean truncated) {
        if (status < 200 || status >= 300) {
            return Json.createObjectBuilder()
                    .add("uri", uri)
                    .add("status", status)
                    .add("error", text == null || text.isBlank()
                            ? "HTTP " + status : text)
                    .build().toString();
        }
        if (!isTextLike(contentType)) {
            return Json.createObjectBuilder()
                    .add("uri", uri)
                    .add("mediaType", contentType == null ? "" : contentType)
                    .add("binary", true)
                    .add("note", "not a text resource - open the URI directly "
                            + "(imagery: use iiif_thumbnail)")
                    .build().toString();
        }
        return Json.createObjectBuilder()
                .add("uri", uri)
                .add("mediaType", contentType == null ? "" : contentType)
                .add("truncated", truncated)
                .add("text", text == null ? "" : text)
                .build().toString();
    }

    /** Whether a media type carries text (parameters stripped, case-insensitive). */
    static boolean isTextLike(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return false;
        }
        String mt = mediaType.toLowerCase(Locale.ROOT);
        int semi = mt.indexOf(';');
        if (semi >= 0) {
            mt = mt.substring(0, semi);
        }
        mt = mt.trim();
        return mt.startsWith("text/")
                || mt.endsWith("+json") || mt.endsWith("+xml") || mt.endsWith("+yaml")
                || TEXT_APPLICATION.contains(mt);
    }
}
