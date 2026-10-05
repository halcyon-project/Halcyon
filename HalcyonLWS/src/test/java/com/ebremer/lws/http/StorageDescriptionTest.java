package com.ebremer.lws.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.lws.json.LwsJson;
import com.ebremer.lws.notify.HttpMessageSignatures;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.vocab.LWS;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the storage description against lws10-core §Storage Description Resource, as it stands in
 * the LWS drafts of 21 September 2026.
 *
 * <p>The shape changed twice in that revision and both changes are load-bearing, so they are
 * asserted rather than left to inspection. w3c/lws-protocol#183 made the description a
 * specialization of a W3C Controlled Identifier document: its {@code @context} is an array
 * beginning with the CID context, its {@code id} is the canonical URI of the storage (which per
 * CID-1.0 is also this document's own URL, so the self-referential {@code StorageDescription}
 * service entry it used to carry has no reason to exist), and a {@code StorageRoot} service is
 * REQUIRED. w3c/lws-protocol#219 renamed the media-type term from {@code mediaType} to
 * {@code format}, including the key of the patch-support map.
 *
 * <p>A real store is opened because the description publishes the webhook verification key, and
 * that key is persisted — asserting the key's id is a URL with a fragment (which
 * lws10-notifications-webhook requires of a signature's {@code keyid}) means asserting against the
 * key the server would actually sign with.
 */
class StorageDescriptionTest {

    private Path dir;
    private LwsStore store;

    @BeforeEach
    void open() throws Exception {
        dir = Files.createTempDirectory("storage-description");
        Constructor<LwsStore> ctor = LwsStore.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        store = ctor.newInstance(dir.resolve("tdb2").toString());
        HttpMessageSignatures.init(store);
    }

    @AfterEach
    void close() {
        try {
            store.raw().close();
        } catch (RuntimeException ignore) {
            // best effort
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignore) {
            // a leftover temp dir is harmless
        }
    }

    private static LwsStorageConfig cfg() {
        return new LwsStorageConfig("/W3Clws", Path.of("build/tmp/description-test"),
                NamingPolicyType.UUID, "https://localhost:8888");
    }

    private JsonObject description() {
        return LwsJson.storageDescription(cfg(), List.of());
    }

    @Test
    void isAControlledIdentifierDocumentIdentifiedByTheStorageUri() {
        JsonObject d = description();

        JsonArray context = d.getJsonArray("@context");
        assertEquals(List.of(LWS.CID_CONTEXT, LWS.CONTEXT),
                context.stream().map(v -> ((JsonString) v).getString()).toList(),
                "@context MUST be an array starting with the CID context then the LWS context");
        assertEquals(cfg().storageRootUri(), d.getString("id"),
                "id MUST be the canonical URI of the storage");
        assertEquals("Storage", d.getString("type"));
    }

    @Test
    void requiresAStorageRootServiceNamingTheRootContainer() {
        JsonObject d = description();

        JsonObject root = entry(d, "service", "StorageRoot");
        assertNotNull(root, "the service set MUST contain a service whose type equals StorageRoot");
        assertEquals(cfg().storageRootUri(), root.getString("serviceEndpoint"));
        assertEquals("StorageRoot",
                d.getJsonArray("service").get(0).asJsonObject().getString("type"),
                "it is listed first: it is the entry point to the hierarchy");
    }

    @Test
    void doesNotAdvertiseADescriptionServiceEntry() {
        // Dereferencing the storage identifier is what yields the description (CID-1.0), so the
        // self-reference is redundant -- and `StorageDescription` is not a vocabulary term.
        assertFalse(has(description(), "service", "StorageDescription"));
    }

    @Test
    void stillAdvertisesTheServicesThisStorageRoutes() {
        JsonObject d = description();

        for (String type : List.of("TypeIndexService", "TypeSearchService", "NotificationService",
                "AccessRequestService", "AccessGrantService")) {
            assertTrue(has(d, "service", type), type + " is advertised");
        }
        assertEquals(List.of("WebhookSubscription"),
                entry(d, "service", "NotificationService").getJsonArray("subscriptionType")
                        .stream().map(v -> ((JsonString) v).getString()).toList(),
                "a NotificationService MUST say which subscription types it supports");
    }

    @Test
    void keysPatchSupportByFormat() {
        JsonObject patch = entry(description(), "capability",
                "https://www.w3.org/ns/lws#PatchSupport");

        assertNotNull(patch);
        assertFalse(patch.containsKey("mediaType"),
                "the term was renamed by w3c/lws-protocol#219; the old key must be gone");
        JsonObject formats = patch.getJsonObject("format");
        // JSON Patch, the format lws10-core requires since w3c/lws-protocol#255, first; merge
        // patch, still accepted, after it, as in the draft's own example.
        for (String format : List.of("application/linkset+json", "application/json")) {
            assertEquals(List.of("application/json-patch+json", "application/merge-patch+json"),
                    formats.getJsonArray(format).stream()
                            .map(v -> ((JsonString) v).getString()).toList(), format);
        }
    }

    @Test
    void advertisesOnlyTheContentNegotiationItPerforms() {
        List<JsonObject> negotiations = description().getJsonArray("capability").stream()
                .map(JsonValue::asJsonObject)
                .filter(o -> "https://www.w3.org/ns/lws#ContentNegotiation"
                        .equals(o.getString("type", null)))
                .toList();

        assertEquals(2, negotiations.size(), "one entry per source media type");
        for (JsonObject n : negotiations) {
            assertTrue(n.getJsonArray("target").stream()
                            .map(v -> ((JsonString) v).getString())
                            .anyMatch(MediaTypes.TURTLE::equals),
                    "every LWS document this storage serves is also available as Turtle");
        }
        assertTrue(negotiations.stream()
                        .anyMatch(n -> MediaTypes.LWS_CID.equals(n.getString("source"))),
                "including the description itself");
    }

    @Test
    void publishesTheWebhookKeyAsAVerificationMethodReferencedFromAuthentication() {
        JsonObject d = description();

        JsonObject vm = d.getJsonArray("verificationMethod").get(0).asJsonObject();
        String id = vm.getString("id");
        assertTrue(id.startsWith(cfg().storageRootUri() + "#"),
                "the keyid MUST be a URL with a fragment, so a receiver can strip the fragment "
                        + "to get the storage identifier and dereference it: " + id);
        assertEquals("JsonWebKey", vm.getString("type"));
        assertEquals(cfg().storageRootUri(), vm.getString("controller"));
        assertEquals(id, HttpMessageSignatures.verificationMethodId(cfg().storageRootUri()),
                "…and it is the same id an outbound signature carries");
        assertEquals(List.of(id), d.getJsonArray("authentication").stream()
                        .map(v -> ((JsonString) v).getString()).toList(),
                "the method MUST be referenced from an authentication verification relationship");

        JsonObject jwk = vm.getJsonObject("publicKeyJwk");
        assertEquals("EC", jwk.getString("kty"));
        assertEquals("P-256", jwk.getString("crv"));
        assertFalse(jwk.containsKey("d"), "a published verification method carries no private key");
    }

    private static JsonObject entry(JsonObject doc, String array, String type) {
        return doc.getJsonArray(array).stream()
                .map(JsonValue::asJsonObject)
                .filter(o -> type.equals(o.getString("type", null)))
                .findFirst().orElse(null);
    }

    private static boolean has(JsonObject doc, String array, String type) {
        return entry(doc, array, type) != null;
    }
}
