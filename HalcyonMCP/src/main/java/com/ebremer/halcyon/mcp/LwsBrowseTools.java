package com.ebremer.halcyon.mcp;

import com.ebremer.lws.client.LwsClient;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

/**
 * MCP-7: {@code lws_list} — one page of a container listing, fetched over the
 * LWS API as the caller. The tool IS an ordinary LWS client (it goes over
 * HTTP with the caller's own token via {@link McpCaller#lwsClient()}), so what
 * it returns is exactly what ACP grants the caller — a 403 is rendered
 * verbatim, not hidden.
 *
 * <p>Pagination follows the protocol, it never fabricates it: the storage
 * serves fixed-size pages whose {@code first}/{@code prev}/{@code next}/
 * {@code last} cursors ride in {@code Link} headers and are opaque
 * (HMAC-sealed). The tool passes those cursors straight back to the client as
 * the {@code cursor} argument to fetch the next page — the same discipline the
 * {@code LWSContainers} tree browser follows. A caller cannot forge a page
 * URI, and the tool refuses any URI outside a configured storage (no open
 * proxy).
 */
public class LwsBrowseTools {

    @Tool(name = "lws_list",
            description = "List one page of a Linked Web Storage container's members "
                    + "(sub-containers and resources) with their media types, sizes and "
                    + "discovered RDF types, as the calling user's access allows. To page, "
                    + "pass the opaque 'next'/'prev'/'first'/'last' cursor this returns back "
                    + "as 'cursor'. Get container URIs from lws_storages / earlier lws_list.")
    public String list(
            @org.springframework.ai.tool.annotation.ToolParam(
                    description = "The container URI to list (a storage root or sub-container).")
            String container,
            @org.springframework.ai.tool.annotation.ToolParam(required = false,
                    description = "An opaque pagination cursor from a previous lws_list, to "
                            + "fetch that page instead of the container's first page.")
            String cursor,
            ToolContext toolContext) {
        McpCaller caller = McpCallers.require(toolContext);

        // The URI actually dereferenced is the cursor when given (it targets a
        // page of this same container), else the container itself. Both must
        // live inside a configured storage — the anti-open-proxy guard.
        String target = cursor != null && !cursor.isBlank() ? cursor : container;
        LwsSupport.requireWithinStorage(target);

        LwsClient.Result r = caller.lwsClient().get(target);
        if (!r.ok()) {
            // The storage's answer is the answer — a 403 is what ACP decided.
            return Json.createObjectBuilder()
                    .add("container", container == null ? "" : container)
                    .add("error", LwsSupport.problem(r.status(), r.body()))
                    .add("status", r.status())
                    .build().toString();
        }
        return formatListing(container, r.body(),
                r.link("first"), r.link("prev"), r.link("next"), r.link("last"));
    }

    /**
     * Reshape one fetched listing to what a model needs — split from the HTTP
     * call so the projection (member kinds, discovered types, the opaque
     * cursors passed through verbatim) is testable without a live storage.
     */
    static String formatListing(String container, JsonObject doc,
            String first, String prev, String next, String last) {
        JsonArrayBuilder items = Json.createArrayBuilder();
        if (doc != null && doc.containsKey("items")) {
            JsonArray raw = doc.getJsonArray("items");
            if (raw != null) {
                raw.forEach(v -> items.add(member(v.asJsonObject())));
            }
        }
        JsonObjectBuilder cursors = Json.createObjectBuilder();
        addCursor(cursors, "first", first);
        addCursor(cursors, "prev", prev);
        addCursor(cursors, "next", next);
        addCursor(cursors, "last", last);

        JsonObjectBuilder out = Json.createObjectBuilder()
                .add("container", container == null ? "" : container);
        if (doc != null && doc.containsKey("totalItems")) {
            out.add("totalItems", doc.getJsonNumber("totalItems").longValue());
        }
        return out.add("items", items)
                .add("cursors", cursors)
                .build().toString();
    }

    /** One listing member, reshaped to what a model needs to act next. */
    private static JsonObject member(JsonObject o) {
        boolean container = false;
        JsonArrayBuilder types = Json.createArrayBuilder();
        JsonValue t = o.get("type");
        if (t != null && t.getValueType() == JsonValue.ValueType.ARRAY) {
            for (JsonValue v : t.asJsonArray()) {
                String s = ((JsonString) v).getString();
                if ("Container".equals(s)) {
                    container = true;
                } else if (!"DataResource".equals(s)) {
                    types.add(s);
                }
            }
        } else if (t != null && t.getValueType() == JsonValue.ValueType.STRING) {
            container = "Container".equals(((JsonString) t).getString());
        }
        JsonObjectBuilder m = Json.createObjectBuilder()
                .add("uri", o.getString("id", ""))
                .add("kind", container ? "container" : "resource")
                .add("mediaType", o.getString("mediaType", ""))
                .add("types", types);
        if (o.containsKey("size")) {
            m.add("size", o.getJsonNumber("size").longValue());
        }
        if (o.containsKey("modified")) {
            m.add("modified", o.getString("modified", ""));
        }
        return m.build();
    }

    private static void addCursor(JsonObjectBuilder b, String rel, String value) {
        if (value != null && !value.isBlank()) {
            b.add(rel, value);
        }
    }
}
