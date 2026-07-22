package com.ebremer.lws.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.capability.CapabilityDescriptor;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.lws.json.LwsJson;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins how the storage description renders capability descriptors: a descriptor's {@code service}
 * and/or {@code capability} entry is advertised only when that capability is installed — a
 * capability entry is a contract, not decoration. The IIIF Image service is the archetype and is
 * used here as the example descriptor.
 */
class StorageDescriptionCapabilityTest {

    private static LwsStorageConfig cfg() {
        return new LwsStorageConfig("/W3Clws", Path.of("build/tmp/iiif-test"),
                NamingPolicyType.UUID, "https://localhost:8888");
    }

    /** An IIIF-shaped descriptor, exactly as the app-side IIIF capability contributes. */
    private static CapabilityDescriptor iiifDescriptor(LwsStorageConfig c) {
        String endpoint = c.baseUri() + "/.iiif";
        return CapabilityDescriptor.of(
                new CapabilityDescriptor.ServiceEntry("ImageService", endpoint,
                        List.of("http://iiif.io/api/image"), null),
                new CapabilityDescriptor.CapabilityEntry("http://iiif.io/api/image", endpoint,
                        "query dialect: ?iiif={imageUri}/…"));
    }

    @Test
    void advertisesServiceAndCapabilityEntriesWhenInstalled() {
        JsonObject with = LwsJson.storageDescription(cfg(), List.of(iiifDescriptor(cfg())));

        assertTrue(hasEntry(with, "service", "type", "ImageService"),
                "an installed capability's service entry is listed");
        assertEquals(cfg().baseUri() + "/.iiif",
                entryValue(with, "service", "type", "ImageService", "serviceEndpoint"));
        assertTrue(hasEntry(with, "capability", "type", "http://iiif.io/api/image"),
                "…and its capability entry");
    }

    @Test
    void advertisesNothingExtraWhenNoCapabilityInstalled() {
        JsonObject without = LwsJson.storageDescription(cfg(), List.of());

        assertFalse(hasEntry(without, "service", "type", "ImageService"),
                "a capability entry is a contract — never advertised when absent");
        assertFalse(hasEntry(without, "capability", "type", "http://iiif.io/api/image"));
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
