package com.ebremer.lws.json;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A resource's linkset: its metadata, as an RFC 9264 {@code application/linkset+json}
 * document.
 *
 * <p>LWS expresses <em>all</em> metadata as typed links from the resource. The linkset
 * is the same information the {@code Link} response headers carry, made addressable as
 * a resource in its own right so that a client can read it, and PATCH it, without
 * touching the resource's content.
 *
 * <p>Shape (RFC 9264): one object per <em>anchor</em>; each relation type is a key whose
 * value is an array of link objects carrying {@code href} and any target attributes.
 *
 * <pre>{@code
 * { "linkset": [ { "anchor": "…/list.txt",
 *                  "up":       [ { "href": "…/notes/" } ],
 *                  "describedby": [ { "href": "…/schema" } ] } ] }
 * }</pre>
 */
public final class LinksetJson {

    private LinksetJson() {
    }

    /** Relations the server owns. A client may not set or remove these. */
    public static final List<String> SERVER_MANAGED =
            List.of("up", "linkset", "type", "acl", "first", "prev", "next", "last");

    /**
     * Build a linkset document.
     *
     * @param anchor the resource being described
     * @param links  relation type -> target URIs, in insertion order
     */
    public static JsonObject build(String anchor, Map<String, List<String>> links) {
        JsonObjectBuilder entry = Json.createObjectBuilder().add("anchor", anchor);
        links.forEach((rel, targets) -> {
            JsonArrayBuilder arr = Json.createArrayBuilder();
            targets.forEach(href -> arr.add(Json.createObjectBuilder().add("href", href)));
            entry.add(rel, arr);
        });
        return Json.createObjectBuilder()
                .add("linkset", Json.createArrayBuilder().add(entry))
                .build();
    }

    /**
     * Apply an RFC 7386 JSON Merge Patch to a set of links.
     *
     * <p>Merge Patch semantics, which is what makes it a good fit here: a key present in
     * the patch replaces that relation wholesale, and a key whose value is
     * <strong>null</strong> removes it. So a client adds a license with
     * {@code {"license": [{"href": "…"}]}} and drops it again with
     * {@code {"license": null}} — no read-modify-write of the whole document, and no way
     * to accidentally clobber a relation it never mentioned.
     *
     * @param current  the resource's user-managed links
     * @param patch    the merge patch, already parsed
     * @param rejected receives any server-managed relation the patch tried to touch
     * @return the new set of user-managed links
     */
    public static Map<String, List<String>> mergePatch(
            Map<String, List<String>> current, JsonObject patch, List<String> rejected) {

        Map<String, List<String>> out = new LinkedHashMap<>(current);

        for (String rel : patch.keySet()) {
            if (SERVER_MANAGED.contains(rel)) {
                // Server-managed metadata "MUST be generated automatically by the server
                // ... and MUST NOT be overridden by client-provided links". Silently
                // ignoring the attempt would leave the client believing it had worked.
                rejected.add(rel);
                continue;
            }
            var value = patch.get(rel);
            if (value == null || value.getValueType() == jakarta.json.JsonValue.ValueType.NULL) {
                out.remove(rel);
                continue;
            }
            List<String> targets = new java.util.ArrayList<>();
            switch (value.getValueType()) {
                case ARRAY -> {
                    for (var v : value.asJsonArray()) {
                        String href = hrefOf(v);
                        if (href != null) {
                            targets.add(href);
                        }
                    }
                }
                case OBJECT, STRING -> {
                    String href = hrefOf(value);
                    if (href != null) {
                        targets.add(href);
                    }
                }
                default -> {
                    // A number or boolean is not a link target; ignore it rather than
                    // storing something no client could dereference.
                }
            }
            if (targets.isEmpty()) {
                out.remove(rel);
            } else {
                out.put(rel, targets);
            }
        }
        return out;
    }

    private static String hrefOf(jakarta.json.JsonValue v) {
        return switch (v.getValueType()) {
            case STRING -> ((jakarta.json.JsonString) v).getString();
            case OBJECT -> {
                JsonObject o = v.asJsonObject();
                yield o.containsKey("href") && o.get("href").getValueType()
                        == jakarta.json.JsonValue.ValueType.STRING
                        ? o.getString("href") : null;
            }
            default -> null;
        };
    }
}
