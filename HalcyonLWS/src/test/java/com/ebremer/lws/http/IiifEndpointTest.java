package com.ebremer.lws.http;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.lws.json.LwsJson;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the storage-side half of the IIIF Image service: the reserved
 * {@code .iiif} endpoint name, the image-identity extraction the ACP check
 * hangs on, and the advertise-only-when-real rule for the storage
 * description's capability/service entries.
 */
class IiifEndpointTest {

    private static LwsStorageConfig cfg() {
        return new LwsStorageConfig("/W3Clws", Path.of("build/tmp/iiif-test"),
                NamingPolicyType.UUID, "https://localhost:8888");
    }

    @Test
    void imageIdentityExtraction() {
        String base = "https://localhost:8888/W3Clws/abc";
        assertEquals(base, LwsServlet.iiifImageUri(base + "/info.json"));
        assertEquals(base, LwsServlet.iiifImageUri(base + "/full/512,/0/default.jpg"));
        assertEquals(base, LwsServlet.iiifImageUri(base + "/0,0,1024,1024/!256,256/90/gray.png"));
        assertNull(LwsServlet.iiifImageUri("no-slashes-here"), "not an IIIF URL shape");
        assertNull(LwsServlet.iiifImageUri("/info.json"), "an empty identity is no identity");
        // Fewer than four request segments and no /info.json → unparseable, not guessed.
        assertNull(LwsServlet.iiifImageUri("a/b/c"));
    }

    @Test
    void targetRoutesTheReservedIiifName() {
        // The slug sanitiser strips leading dots, so no client-minted resource can
        // collide with the endpoint — same reservation contract as .description.
        LwsStorageConfig c = cfg();
        assertEquals("https://localhost:8888/W3Clws/.iiif", c.iiifUri());
        assertTrue(c.iiifUri().startsWith(c.baseUri()));
    }

    @Test
    void descriptionAdvertisesTheImageServiceOnlyWhenInstalled() {
        JsonObject with = LwsJson.storageDescription(cfg(), true);
        JsonObject without = LwsJson.storageDescription(cfg(), false);

        assertTrue(hasEntry(with, "capability", "type", "http://iiif.io/api/image"),
                "an installed image service is a capability of the storage");
        assertTrue(hasEntry(with, "service", "type", "ImageService"),
                "…and a listed service endpoint");
        assertEquals(cfg().iiifUri(), entryValue(with, "service", "type", "ImageService",
                "serviceEndpoint"));

        assertFalse(hasEntry(without, "capability", "type", "http://iiif.io/api/image"),
                "a capability entry is a contract — never advertised when absent");
        assertFalse(hasEntry(without, "service", "type", "ImageService"));
    }

    private static boolean hasEntry(JsonObject doc, String array, String key, String value) {
        return doc.getJsonArray(array).stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(o -> value.equals(o.getString(key, null)));
    }

    private static String entryValue(JsonObject doc, String array, String key, String value,
            String field) {
        return doc.getJsonArray(array).stream()
                .map(JsonValue::asJsonObject)
                .filter(o -> value.equals(o.getString(key, null)))
                .findFirst().orElseThrow()
                .getString(field);
    }
}
