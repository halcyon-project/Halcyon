package com.ebremer.halcyon.mcp;

import com.ebremer.lws.client.LwsClient;
import com.ebremer.lws.config.LwsStorageConfig;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * MCP-13: {@code lws_request_access} — file an LWS <em>access request</em> for
 * a resource, as the caller. This is how an agent that was refused (a 403 from
 * any read tool) asks for access the protocol way: a POST to the storage's
 * Data Sharing Service ({@code .access/requests}).
 *
 * <p>The request grants nothing by itself — it records an ODRL/ActivityStreams
 * {@code AccessRequest} (the caller's WebID as {@code assignee}, the resource
 * as {@code target}, the requested actions), and a storage controller answers
 * it with a grant. It is the exact document the storage's own
 * {@code AccessRequestPanel} posts; the tool just lets an agent file it for
 * itself.
 */
public class LwsAccessTools {

    /** The actions an access request may name (LWS Data Sharing Service). */
    private static final Set<String> ALLOWED = Set.of("read", "modify", "create", "delete");

    @Tool(name = "lws_request_access",
            description = "File an access request for a Linked Web Storage resource on your own "
                    + "behalf - use this when a resource is forbidden (403) and you need access. "
                    + "It records the request with the storage's data-sharing service; it grants "
                    + "nothing until a storage controller approves it. Actions: any of "
                    + "read, modify, create, delete (comma-separated; default read).")
    public String requestAccess(
            @ToolParam(description = "The resource URI to request access to.")
            String resource,
            @ToolParam(required = false,
                    description = "Comma-separated actions: read, modify, create, delete "
                            + "(default read).")
            String actions,
            ToolContext toolContext) {
        McpCaller caller = McpCallers.require(toolContext);
        LwsStorageConfig cfg = LwsSupport.requireWithinStorage(resource);

        Set<String> requested;
        try {
            requested = parseActions(actions);
        } catch (IllegalArgumentException e) {
            return Json.createObjectBuilder()
                    .add("resource", resource).add("error", e.getMessage()).build().toString();
        }

        String webId = caller.webId();
        if (webId == null || webId.isBlank()) {
            return Json.createObjectBuilder()
                    .add("resource", resource)
                    .add("error", "the caller has no WebID to assign the request to")
                    .build().toString();
        }

        String doc = accessRequestJson(webId, resource, requested);
        LwsClient.Result r = caller.lwsClient().post(cfg.accessRequestsUri(), null,
                "application/lws+json", doc.getBytes(StandardCharsets.UTF_8), false);
        if (r.status() == 201) {
            return Json.createObjectBuilder()
                    .add("resource", resource)
                    .add("recorded", true)
                    .add("location", r.location() == null ? "" : r.location())
                    .add("note", "recorded; a storage controller must approve it before it grants "
                            + "anything")
                    .build().toString();
        }
        return Json.createObjectBuilder()
                .add("resource", resource)
                .add("status", r.status())
                .add("error", LwsSupport.problem(r.status(), r.body()))
                .build().toString();
    }

    /**
     * Parse and validate the requested actions, preserving order and
     * defaulting to {@code read}. An unknown action is refused — the request
     * must not be filed with a term the service will not understand.
     */
    static Set<String> parseActions(String actions) {
        if (actions == null || actions.isBlank()) {
            return Set.of("read");
        }
        Set<String> out = new LinkedHashSet<>();
        for (String a : actions.split(",")) {
            String action = a.trim().toLowerCase(Locale.ROOT);
            if (action.isEmpty()) {
                continue;
            }
            if (!ALLOWED.contains(action)) {
                throw new IllegalArgumentException("unknown action '" + action
                        + "'; allowed: read, modify, create, delete");
            }
            out.add(action);
        }
        if (out.isEmpty()) {
            return Set.of("read");
        }
        return out;
    }

    /**
     * The ActivityStreams {@code AccessRequest} document — the same shape the
     * storage's {@code AccessRequestPanel} posts. Split out so the payload is
     * testable without a live Data Sharing Service.
     */
    static String accessRequestJson(String webId, String resource, Set<String> actions) {
        JsonArrayBuilder actionArr = Json.createArrayBuilder();
        actions.forEach(actionArr::add);
        return Json.createObjectBuilder()
                .add("@context", "https://www.w3.org/ns/activitystreams")
                .add("type", "AccessRequest")
                .add("access", Json.createArrayBuilder().add(Json.createObjectBuilder()
                        .add("action", actionArr)
                        .add("assignee", webId)
                        .add("target", Json.createObjectBuilder()
                                .add("value", Json.createArrayBuilder().add(resource)))))
                .build().toString();
    }
}
