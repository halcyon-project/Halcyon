package com.ebremer.halcyon.mcp;

import com.ebremer.lws.client.LwsClient;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
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
 * MCP-10: the Halcyon-specific discovery layer — {@code find_slides} and
 * {@code list_stacks} — over the LWS <strong>Type Search</strong> service.
 *
 * <p>These do not walk the container tree: they {@code QUERY} each storage's
 * {@code .types/search} endpoint for a scanner-discovered RDF type, which
 * searches the whole storage in one request and is <strong>ACP-filtered by
 * the storage itself</strong> — the caller sees a match only where their
 * WebID is granted read. Whole-slide imagery is typed
 * {@code schema:ImageObject} by the metadata scanner (every image reader
 * emits it); an LWS-native Zephyr stack types itself {@code zeph:Stack}.
 * Each match carries the URIs a client needs next (the resource, its media
 * type, its storage's IIIF endpoint), so a model can go from "find slides" to
 * {@code iiif_thumbnail} without guessing.
 */
public class LwsDiscoveryTools {

    /** What the metadata scanner types imagery as (every image reader emits it). */
    static final String IMAGE_OBJECT = "https://schema.org/ImageObject";
    /** What an LWS-native Zephyr stack types itself. */
    static final String ZEPH_STACK = "https://halcyon.is/zephyr/ns/Stack";

    @Tool(name = "find_slides",
            description = "Find whole-slide / pyramidal images (schema:ImageObject) across this "
                    + "server's storages, as the calling user's access allows. Each match gives "
                    + "the image URI, its media type, and the storage IIIF endpoint to view it "
                    + "with iiif_info / iiif_thumbnail.")
    public String findSlides(ToolContext toolContext) {
        return search(McpCallers.require(toolContext), IMAGE_OBJECT);
    }

    @Tool(name = "list_stacks",
            description = "List Zephyr annotation stacks (zeph:Stack) across this server's "
                    + "storages, as the calling user's access allows. Each match gives the stack "
                    + "URI (a Turtle document you can read with lws_read) and its container.")
    public String listStacks(ToolContext toolContext) {
        return search(McpCallers.require(toolContext), ZEPH_STACK);
    }

    /** Type Search every configured storage as the caller, collect the matches. */
    private static String search(McpCaller caller, String typeIri) {
        String filter = Json.createObjectBuilder()
                .add("type", Json.createArrayBuilder().add(typeIri))
                .build().toString();
        LwsClient client = caller.lwsClient();

        JsonArrayBuilder storages = Json.createArrayBuilder();
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            LwsClient.Result r = client.query(cfg.typeSearchUri(), filter);
            JsonObjectBuilder s = Json.createObjectBuilder()
                    .add("root", cfg.storageRootUri())
                    .add("iiif", cfg.iiifUri());
            if (r.ok()) {
                JsonObject body = r.body();
                if (body != null && body.containsKey("totalItems")) {
                    s.add("totalItems", body.getJsonNumber("totalItems").longValue());
                }
                s.add("matches", matchItems(body));
            } else {
                s.add("error", LwsSupport.problem(r.status(), r.body()));
            }
            storages.add(s);
        }
        return Json.createObjectBuilder()
                .add("type", typeIri)
                .add("storages", storages)
                .build().toString();
    }

    /** The {@code items} of a Type Search answer, reshaped to what a client acts on. */
    static JsonArray matchItems(JsonObject searchBody) {
        JsonArrayBuilder out = Json.createArrayBuilder();
        if (searchBody != null && searchBody.containsKey("items")) {
            JsonArray items = searchBody.getJsonArray("items");
            if (items != null) {
                for (JsonValue v : items) {
                    JsonObject o = v.asJsonObject();
                    JsonArrayBuilder types = Json.createArrayBuilder();
                    JsonValue t = o.get("type");
                    if (t != null && t.getValueType() == JsonValue.ValueType.ARRAY) {
                        for (JsonValue tv : t.asJsonArray()) {
                            String s = ((JsonString) tv).getString();
                            if (!"Container".equals(s) && !"DataResource".equals(s)) {
                                types.add(s);
                            }
                        }
                    }
                    out.add(Json.createObjectBuilder()
                            .add("uri", o.getString("id", ""))
                            .add("mediaType", o.getString("mediaType", ""))
                            .add("types", types));
                }
            }
        }
        return out.build();
    }
}
