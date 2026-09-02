package com.ebremer.lws.auth.oidc;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LwsOidcSettings} data model — focused on the Option B local WebID-&gt;role map (the
 * OP's token is never consulted for local groups) and its defensive copying.
 */
class LwsOidcSettingsTest {

    private static final String WEBID = "https://ebremer.com/id/erich";

    @Test
    void groupsForReturnsTheConfiguredGroups() {
        LwsOidcSettings s = new LwsOidcSettings(true, Set.of(), "halcyon-local", false,
                Map.of(WEBID, Set.of("admin")));
        assertEquals(Set.of("admin"), s.groupsFor(WEBID));
    }

    @Test
    void groupsForIsEmptyForAnUnmappedWebId() {
        LwsOidcSettings s = new LwsOidcSettings(true, Set.of(), "halcyon-local", false,
                Map.of(WEBID, Set.of("admin")));
        assertTrue(s.groupsFor("https://someone-else.example/#me").isEmpty());
    }

    @Test
    void theTwoArgFormDefaultsToNoRoleMap() {
        LwsOidcSettings s = new LwsOidcSettings(true, Set.of("127.0.0.1"));
        assertTrue(s.webIdGroups().isEmpty());
        assertEquals("halcyon-local", s.webIdLoginClientId());
        assertTrue(s.groupsFor(WEBID).isEmpty());
    }

    @Test
    void theRoleMapIsDefensivelyCopiedAndImmutable() {
        LwsOidcSettings s = new LwsOidcSettings(true, Set.of(), "halcyon-local", false,
                Map.of(WEBID, Set.of("admin")));
        assertThrows(UnsupportedOperationException.class,
                () -> s.webIdGroups().put("https://x.example/#me", Set.of("admin")));
    }

    @Test
    void nullRoleMapBecomesEmpty() {
        LwsOidcSettings s = new LwsOidcSettings(true, Set.of(), "halcyon-local", false, null);
        assertTrue(s.webIdGroups().isEmpty());
    }
}
