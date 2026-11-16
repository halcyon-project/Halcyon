package com.ebremer.lws.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.vocab.LWS;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins a container representation against lws10-core §Container Representation.
 *
 * <p>A contained resource description MUST carry {@code id} and {@code type}, and SHOULD carry
 * {@code format} (MUST for a data resource), {@code size} and {@code modified}. The media-type
 * member was called {@code mediaType} until w3c/lws-protocol#219 moved these terms off Activity
 * Streams onto Dublin Core and schema.org; every representation this module emits uses the new
 * spelling, and a client reading the old one would silently see no media types at all — so the
 * rename is asserted rather than assumed.
 */
class LwsJsonTest {

    private static final String C = "https://localhost:8888/W3Clws/notes/";

    @Test
    void aContainerCarriesItsIdTypeTotalItemsAndItems() {
        JsonObject c = LwsJson.container(C, 2, List.of(
                new LwsJson.Item(C + "list.txt", List.of("DataResource"), "text/plain", 47L,
                        "2026-09-21T12:00:00Z"),
                new LwsJson.Item(C + "sub/", List.of("Container"), null, null,
                        "2026-09-21T13:00:00Z")));

        assertEquals(LWS.CONTEXT, c.getString("@context"));
        assertEquals(C, c.getString("id"));
        assertEquals("Container", c.getString("type"));
        assertEquals(2, c.getInt("totalItems"));
        assertEquals(2, c.getJsonArray("items").size());
    }

    @Test
    void aDataResourceMemberCarriesItsMediaTypeAsFormat() {
        JsonObject item = LwsJson.container(C, 1, List.of(
                        new LwsJson.Item(C + "list.txt", List.of("DataResource"), "text/plain",
                                47L, "2026-09-21T12:00:00Z")))
                .getJsonArray("items").get(0).asJsonObject();

        assertEquals("text/plain", item.getString("format"));
        assertFalse(item.containsKey("mediaType"),
                "the pre-#219 spelling must not be emitted alongside or instead");
        assertEquals(47L, item.getJsonNumber("size").longValue());
        assertEquals("2026-09-21T12:00:00Z", item.getString("modified"));
        assertEquals("DataResource", item.getString("type"));
    }

    @Test
    void aContainerMemberHasNoFormatOfItsOwn() {
        JsonObject item = LwsJson.container(C, 1, List.of(
                        new LwsJson.Item(C + "sub/", List.of("Container"), null, null, null)))
                .getJsonArray("items").get(0).asJsonObject();

        assertFalse(item.containsKey("format"),
                "a container has no representation to give a media type to");
        assertFalse(item.containsKey("size"));
    }

    @Test
    void discoveredTypesRideAlongsideTheStructuralOne() {
        JsonObject item = LwsJson.container(C, 1, List.of(
                        new LwsJson.Item(C + "p.json",
                                List.of("DataResource", "https://schema.org/Person"),
                                "application/json", 12L, null)))
                .getJsonArray("items").get(0).asJsonObject();

        assertEquals(JsonValue.ValueType.ARRAY, item.get("type").getValueType());
        List<String> types = item.getJsonArray("type").stream()
                .map(v -> ((JsonString) v).getString()).toList();
        assertEquals(List.of("DataResource", "https://schema.org/Person"), types,
                "type MUST contain DataResource or Container, and MAY carry user-defined URIs");
    }

    @Test
    void aSearchResultSetIdentifiesNoContainer() {
        JsonObject page = LwsJson.containerPage(27, List.of(
                new LwsJson.Item(C + "p.json", List.of("DataResource"), "application/json", 12L,
                        null)));

        assertEquals("ContainerPage", page.getString("type"));
        assertFalse(page.containsKey("id"),
                "a result set is synthetic: it identifies no container and is not retrievable");
        assertEquals(27, page.getInt("totalItems"));
    }

    @Test
    void aTypeIndexListsOnlyTypeIdentifiers() {
        JsonObject index = LwsJson.typeIndex(2,
                List.of("https://schema.org/Person", LWS.DataResource.getURI()));

        assertEquals("TypeIndex", index.getString("type"));
        assertEquals(2, index.getInt("totalItems"));
        assertTrue(index.getJsonArray("items").stream()
                        .allMatch(v -> v.asJsonObject().keySet().equals(java.util.Set.of("id"))),
                "each item carries only an id");
    }
}
