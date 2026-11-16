package com.ebremer.lws.auth.oidc;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LwsOidcSettings} data model — focused on the Option B local CID URL-&gt;role map (the
 * OP's token is never consulted for local groups) and its defensive copying — and how
 * {@code lws-oidc.json} is read, including the keys deprecated by the WebID-to-CID-URL rename.
 */
class LwsOidcSettingsTest {

    private static final String CID_URL = "https://ebremer.com/id/erich";

    @Test
    void groupsForReturnsTheConfiguredGroups() {
        LwsOidcSettings s = new LwsOidcSettings(true, Set.of(), "halcyon-local", false,
                Map.of(CID_URL, Set.of("admin")));
        assertEquals(Set.of("admin"), s.groupsFor(CID_URL));
    }

    @Test
    void groupsForIsEmptyForAnUnmappedCidUrl() {
        LwsOidcSettings s = new LwsOidcSettings(true, Set.of(), "halcyon-local", false,
                Map.of(CID_URL, Set.of("admin")));
        assertTrue(s.groupsFor("https://someone-else.example/#me").isEmpty());
    }

    @Test
    void theTwoArgFormDefaultsToNoRoleMap() {
        LwsOidcSettings s = new LwsOidcSettings(true, Set.of("127.0.0.1"));
        assertTrue(s.cidGroups().isEmpty());
        assertEquals("halcyon-local", s.cidLoginClientId());
        assertEquals(LwsOidcSettings.CALLBACK_PATH, s.callbackPath());
        assertTrue(s.groupsFor(CID_URL).isEmpty());
    }

    @Test
    void theRoleMapIsDefensivelyCopiedAndImmutable() {
        LwsOidcSettings s = new LwsOidcSettings(true, Set.of(), "halcyon-local", false,
                Map.of(CID_URL, Set.of("admin")));
        assertThrows(UnsupportedOperationException.class,
                () -> s.cidGroups().put("https://x.example/#me", Set.of("admin")));
    }

    @Test
    void nullRoleMapBecomesEmpty() {
        LwsOidcSettings s = new LwsOidcSettings(true, Set.of(), "halcyon-local", false, null);
        assertTrue(s.cidGroups().isEmpty());
    }

    @Test
    void readsTheCidUrlKeysAndSendsTheNewRedirectUri() {
        LwsOidcSettings s = LwsOidcSettings.from(json("""
                { "enabled": true, "cidLoginClientId": "storage-ui", "cidLoginDynamicRegistration": false,
                  "cidGroups": { "%s": ["admin"] } }
                """.formatted(CID_URL)));
        assertEquals("storage-ui", s.cidLoginClientId());
        assertEquals(Set.of("admin"), s.groupsFor(CID_URL));
        assertEquals("/cid-callback", s.callbackPath());
    }

    @Test
    void stillReadsTheDeprecatedWebIdKeys() {
        LwsOidcSettings s = LwsOidcSettings.from(json("""
                { "enabled": true, "webIdLoginClientId": "storage-ui", "webIdLoginDynamicRegistration": true,
                  "webIdGroups": { "%s": ["admin"] } }
                """.formatted(CID_URL)));
        assertEquals("storage-ui", s.cidLoginClientId());
        assertTrue(s.cidLoginDynamicRegistration());
        assertEquals(Set.of("admin"), s.groupsFor(CID_URL));
    }

    @Test
    void aNewKeyWinsOverItsDeprecatedName() {
        LwsOidcSettings s = LwsOidcSettings.from(json("""
                { "cidLoginClientId": "new", "webIdLoginClientId": "old",
                  "cidGroups": { "%s": ["admin"] }, "webIdGroups": { "%s": ["admin"] } }
                """.formatted(CID_URL, "https://old.example/#me")));
        assertEquals("new", s.cidLoginClientId());
        assertEquals(Set.of(CID_URL), s.cidGroups().keySet());
    }

    /**
     * A pre-arranged client is registered at its OP with a fixed redirect URI, so a file that has
     * not named it with the new key keeps sending the old one and an existing registration keeps
     * matching. That covers both the deprecated key and the default client.
     */
    @Test
    void aPreArrangedClientConfiguredTheOldWayKeepsTheOldRedirectUri() {
        assertEquals("/webid-callback", LwsOidcSettings.from(json("""
                { "enabled": true, "webIdLoginClientId": "halcyon-local" }
                """)).callbackPath());
        assertEquals("/webid-callback", LwsOidcSettings.from(json("""
                { "enabled": true }
                """)).callbackPath());
    }

    /** Dynamic registration registers whatever it sends, so it moves to the new URI whatever the keys. */
    @Test
    void dynamicRegistrationAlwaysSendsTheNewRedirectUri() {
        assertEquals("/cid-callback", LwsOidcSettings.from(json("""
                { "enabled": true, "webIdLoginDynamicRegistration": true }
                """)).callbackPath());
    }

    private static JsonObject json(String s) {
        return Json.createReader(new StringReader(s)).readObject();
    }
}
