package com.ebremer.lws.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.vocab.LWS;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins which linkset relations the server owns.
 *
 * <p>lws10-core lists the storage link among the metadata a server must make discoverable, and the
 * linkset is where a resource's metadata is addressable. It follows that the relation is
 * server-managed: if a client could PATCH it, a resource could claim to belong to a storage it
 * does not, and a client trusting the linkset over the Link header would then authenticate against
 * the wrong authorization server.
 */
class LinksetJsonTest {

    private static final String STORAGE = "https://localhost:8888/W3Clws/";
    private static final String R = STORAGE + "notes/list.txt";

    @Test
    void theStorageRelationIsServerManaged() {
        assertTrue(LinksetJson.SERVER_MANAGED.contains(LWS.REL_STORAGE));
    }

    @Test
    void aPatchTouchingTheStorageRelationIsRejectedNotIgnored() {
        Map<String, List<String>> current = new LinkedHashMap<>();
        current.put("license", List.of("https://example.org/cc-by"));
        JsonObject patch = read("{\"" + LWS.REL_STORAGE + "\": [{\"href\": \"https://evil/\"}]}");
        List<String> rejected = new ArrayList<>();

        Map<String, List<String>> out = LinksetJson.mergePatch(current, patch, rejected);

        assertEquals(List.of(LWS.REL_STORAGE), rejected,
                "silently ignoring it would leave the client believing it had worked");
        assertEquals(current, out);
    }

    @Test
    void aUserManagedRelationIsReplacedWholesaleAndRemovedByNull() {
        Map<String, List<String>> current = new LinkedHashMap<>();
        current.put("license", List.of("https://example.org/cc-by"));
        current.put("describedby", List.of("https://example.org/schema"));

        Map<String, List<String>> added = LinksetJson.mergePatch(current,
                read("{\"license\": [{\"href\": \"https://example.org/cc0\"}]}"),
                new ArrayList<>());
        assertEquals(List.of("https://example.org/cc0"), added.get("license"));
        assertEquals(List.of("https://example.org/schema"), added.get("describedby"),
                "a relation the patch never mentioned is untouched");

        Map<String, List<String>> dropped = LinksetJson.mergePatch(current,
                read("{\"license\": null}"), new ArrayList<>());
        assertTrue(!dropped.containsKey("license"));
    }

    /** The RFC 9264 document a GET returns reads as the same relations as the relation map. */
    @Test
    void aLinksetDocumentPatchIsReadAsItsRelations() {
        Map<String, List<String>> server = Map.of("up", List.of(STORAGE + "notes/"));
        List<String> rejected = new ArrayList<>();

        JsonObject relations = LinksetJson.relations(read("{\"linkset\": [{\"anchor\": \"" + R
                + "\", \"license\": [{\"href\": \"https://example.org/cc0\"}]}]}"), R, server,
                rejected);

        assertEquals(List.of(), rejected);
        assertEquals(read("{\"license\": [{\"href\": \"https://example.org/cc0\"}]}"), relations);
    }

    /** Sending back what was read is not an attempt to set a server-managed relation; changing it is. */
    @Test
    void aServerManagedRelationInADocumentPatchMayOnlyBeEchoed() {
        Map<String, List<String>> server = Map.of("up", List.of(STORAGE + "notes/"));

        List<String> echoed = new ArrayList<>();
        JsonObject relations = LinksetJson.relations(read("{\"linkset\": [{\"anchor\": \"" + R
                + "\", \"up\": [{\"href\": \"" + STORAGE + "notes/\"}],"
                + " \"license\": [{\"href\": \"https://example.org/cc0\"}]}]}"), R, server, echoed);
        assertEquals(List.of(), echoed);
        assertEquals(java.util.Set.of("license"), relations.keySet());

        List<String> changed = new ArrayList<>();
        LinksetJson.relations(read("{\"linkset\": [{\"anchor\": \"" + R
                + "\", \"up\": [{\"href\": \"" + STORAGE + "\"}]}]}"), R, server, changed);
        assertEquals(List.of("up"), changed);
    }

    @Test
    void aDocumentPatchDescribingAnotherResourceIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> LinksetJson.relations(
                read("{\"linkset\": [{\"anchor\": \"" + STORAGE + "other\", \"license\": null}]}"),
                R, Map.of(), new ArrayList<>()));
    }

    /** A relation map that tries to set the server-managed linkset relation is still refused as one. */
    @Test
    void aRelationMapSettingTheLinksetRelationIsRejected() {
        List<String> rejected = new ArrayList<>();
        LinksetJson.relations(read("{\"linkset\": [{\"href\": \"https://evil/\"}]}"), R,
                Map.of(), rejected);
        assertEquals(List.of("linkset"), rejected);
    }

    @Test
    void aLinksetIsAnchoredOnTheResourceItDescribes() {
        Map<String, List<String>> links = new LinkedHashMap<>();
        links.put(LWS.REL_STORAGE, List.of(STORAGE));
        links.put("up", List.of(STORAGE + "notes/"));

        JsonObject entry = LinksetJson.build(R, links)
                .getJsonArray("linkset").get(0).asJsonObject();

        assertEquals(R, entry.getString("anchor"));
        assertEquals(STORAGE, entry.getJsonArray(LWS.REL_STORAGE).get(0).asJsonObject()
                .getString("href"));
    }

    private static JsonObject read(String json) {
        try (var r = Json.createReader(new StringReader(json))) {
            return r.readObject();
        }
    }
}
