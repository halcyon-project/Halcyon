package com.ebremer.halcyon.mcp;

import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

/**
 * MCP-6: {@code lws_storages} — the entry point every other LWS tool starts
 * from. It lists the W3C Linked Web Storage roots this server configures,
 * each with the protocol endpoints a client needs next (type search for
 * discovery, the Data Sharing Service for access requests, the IIIF image
 * service, the storage description). This is configuration, not stored data —
 * it discloses only what a storage's own {@code .description} would — but it
 * still requires a verified caller, because there is no anonymous tier of
 * tools on this endpoint (MCP-1).
 *
 * <p>What it does NOT do is decide what the caller may read <em>inside</em> a
 * storage: that is ACP's call, made by the storage itself on every subsequent
 * request the caller makes as themselves.
 */
public class LwsStorageTools {

    @Tool(name = "lws_storages",
            description = "List the Linked Web Storage roots this Halcyon server hosts, each "
                    + "with its type-search, access-request, IIIF image, and description "
                    + "endpoints. Start here, then use lws_list to browse a storage.")
    public String storages(ToolContext toolContext) {
        // A verified caller is required even though the answer is configuration
        // — the endpoint has no anonymous tier.
        McpCallers.require(toolContext);

        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            JsonObjectBuilder o = Json.createObjectBuilder()
                    .add("root", cfg.storageRootUri())
                    .add("description", cfg.descriptionUri())
                    .add("typeSearch", cfg.typeSearchUri())
                    .add("accessRequests", cfg.accessRequestsUri())
                    .add("iiif", cfg.iiifUri());
            arr.add(o);
        }
        return Json.createObjectBuilder()
                .add("storages", arr)
                .build().toString();
    }
}
