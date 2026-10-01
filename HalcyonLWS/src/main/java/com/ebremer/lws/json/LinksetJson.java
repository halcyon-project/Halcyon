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

    /**
     * Relations the server owns. A client may not set or remove these.
     *
     * <p>The storage relation is among them: lws10-core lists the storage link as metadata a
     * server must make discoverable, and letting a client rewrite it in the linkset would let
     * a resource claim to belong to a storage it does not.
     */
    public static final List<String> SERVER_MANAGED =
            List.of("up", "linkset", "type", "acl", "first", "prev", "next", "last",
                    com.ebremer.lws.vocab.LWS.REL_STORAGE);

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
     * The relations a linkset merge patch sets, as a flat {@code relation -> value} object.
     *
     * <p>A client may write the patch in either of two shapes, and both mean the same thing:
     * <ul>
     *   <li>the <strong>relation map</strong>, {@code {"license": [{"href": "…"}]}}, keyed by
     *       relation type as {@link #mergePatch} reads it; or</li>
     *   <li>the <strong>linkset document</strong>, {@code {"linkset": [{"anchor": "…",
     *       "license": [{"href": "…"}]}]}}: the RFC 9264 form a GET returns. A merge patch is
     *       defined over the target's own representation (RFC 7386), so a client that edits what
     *       it read must be understood.</li>
     * </ul>
     *
     * <p>In the document form the {@code linkset} array is read as what it is, a list of context
     * objects keyed by {@code anchor}, and each entry is merged into this resource's links one
     * relation at a time, exactly as the relation map is: a relation the entry leaves out is left
     * alone, and {@code null} removes one. (A literal RFC 7386 array replacement would make every
     * such patch delete the links it did not repeat, server-managed ones included, which no
     * client means.) Every entry must describe this resource.
     *
     * <p>A server-managed relation in the document form is accepted only as an echo: one whose
     * targets equal the current ones in {@code serverLinks} is dropped, so a client can send back
     * what it read; any other value lands in {@code rejected}. In the relation map, naming a
     * server-managed relation at all is a rejection, as before.
     *
     * @param patch       the parsed merge patch
     * @param anchor      the resource the linkset describes
     * @param serverLinks the server-managed links currently derived for it
     * @param rejected    receives the server-managed relations the patch tried to change
     * @return the relation map to hand to {@link #mergePatch}
     * @throws IllegalArgumentException if the document form is malformed or describes another
     *                                  resource
     */
    public static JsonObject relations(JsonObject patch, String anchor,
            Map<String, List<String>> serverLinks, List<String> rejected) {
        if (!isDocument(patch)) {
            patch.keySet().stream().filter(SERVER_MANAGED::contains).forEach(rejected::add);
            return patch;
        }
        if (patch.size() != 1) {
            throw new IllegalArgumentException("a linkset document has no member but linkset");
        }
        JsonObjectBuilder out = Json.createObjectBuilder();
        for (jakarta.json.JsonValue v : patch.getJsonArray("linkset")) {
            JsonObject entry = v.asJsonObject();
            if (!anchor.equals(entry.getString("anchor"))) {
                throw new IllegalArgumentException("this linkset describes " + anchor
                        + " only, not " + entry.getString("anchor"));
            }
            for (String rel : entry.keySet()) {
                if ("anchor".equals(rel)) {
                    continue;
                }
                var value = entry.get(rel);
                if (SERVER_MANAGED.contains(rel)) {
                    if (!sameTargets(value, serverLinks.get(rel)) && !rejected.contains(rel)) {
                        rejected.add(rel);
                    }
                    continue;
                }
                out.add(rel, value);
            }
        }
        return out.build();
    }

    /**
     * True if {@code patch} is a linkset document rather than a relation map: a {@code linkset}
     * member holding an array of objects that each name an {@code anchor}. A relation map trying
     * to set the server-managed {@code linkset} relation would hold link targets instead, and is
     * left to be refused as one.
     */
    private static boolean isDocument(JsonObject patch) {
        var ls = patch.get("linkset");
        if (ls == null || ls.getValueType() != jakarta.json.JsonValue.ValueType.ARRAY
                || ls.asJsonArray().isEmpty()) {
            return false;
        }
        for (jakarta.json.JsonValue v : ls.asJsonArray()) {
            if (v.getValueType() != jakarta.json.JsonValue.ValueType.OBJECT
                    || !v.asJsonObject().containsKey("anchor")
                    || v.asJsonObject().get("anchor").getValueType()
                            != jakarta.json.JsonValue.ValueType.STRING) {
                return false;
            }
        }
        return true;
    }

    /** True if a patch value names exactly {@code current}'s targets, in any order. */
    private static boolean sameTargets(jakarta.json.JsonValue value, List<String> current) {
        if (value == null || value.getValueType() == jakarta.json.JsonValue.ValueType.NULL) {
            return current == null || current.isEmpty();
        }
        List<String> hrefs = new java.util.ArrayList<>();
        if (value.getValueType() == jakarta.json.JsonValue.ValueType.ARRAY) {
            for (var v : value.asJsonArray()) {
                hrefs.add(hrefOf(v));
            }
        } else {
            hrefs.add(hrefOf(value));
        }
        return current != null && new java.util.HashSet<>(hrefs).equals(new java.util.HashSet<>(current))
                && hrefs.size() == current.size();
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
