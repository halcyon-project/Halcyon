package com.ebremer.lws.acp;

import static com.ebremer.lws.acp.AccessMode.APPEND;
import static com.ebremer.lws.acp.AccessMode.CONTROL;
import static com.ebremer.lws.acp.AccessMode.READ;
import static com.ebremer.lws.acp.AccessMode.WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The ACP mode algebra — {@link AccessMode#effective}. This is the logic C4's inbox test found to be
 * wrong, and it is the kind of pure, high-consequence function a live-server harness verifies only
 * indirectly. Deny-beats-allow, {@code Write ⟹ Append} running one way only, and the implication
 * never resurrecting a denied mode are each pinned here so a refactor cannot quietly re-break them.
 */
class AccessModeTest {

    private static Set<AccessMode> eff(Set<AccessMode> allow, Set<AccessMode> deny) {
        return AccessMode.effective(allow, deny);
    }

    @Test
    void allowReadGrantsOnlyRead() {
        assertEquals(EnumSet.of(READ), eff(Set.of(READ), Set.of()));
    }

    @Test
    void writeImpliesAppend() {
        assertEquals(EnumSet.of(WRITE, APPEND), eff(Set.of(WRITE), Set.of()),
                "a granted Write must carry Append: replacing a container's contents without being "
                        + "allowed to add to it is incoherent");
    }

    @Test
    void denyBeatsAllow() {
        // Write and Read allowed, Write denied: Write goes, and so does the Append it would imply.
        assertEquals(EnumSet.of(READ), eff(Set.of(READ, WRITE), Set.of(WRITE)));
    }

    @Test
    void inboxPatternIsExpressible() {
        // THE C4 property. Append allowed, Write denied -> Append SURVIVES. "You may post here but
        // not look inside or overwrite." A previous version applied Write⟹Append to the deny set,
        // which silently withdrew Append whenever Write was withheld and made this inexpressible.
        assertEquals(EnumSet.of(APPEND), eff(Set.of(APPEND), Set.of(WRITE)));
    }

    @Test
    void implicationCannotResurrectADeniedMode() {
        // Write allowed, Append explicitly denied: keep Write, but the Write⟹Append implication
        // must not hand Append back.
        assertEquals(EnumSet.of(WRITE), eff(Set.of(WRITE), Set.of(APPEND)));
    }

    @Test
    void nothingAllowedGrantsNothing() {
        assertEquals(EnumSet.noneOf(AccessMode.class), eff(Set.of(), Set.of()));
        assertEquals(EnumSet.noneOf(AccessMode.class), eff(Set.of(), Set.of(READ, WRITE)));
    }

    @Test
    void denyingAnUngrantedModeIsHarmless() {
        assertEquals(EnumSet.of(READ), eff(Set.of(READ), Set.of(CONTROL)));
    }

    @Test
    void fullControlSurvivesIntact() {
        assertEquals(EnumSet.of(READ, WRITE, APPEND, CONTROL),
                eff(Set.of(READ, WRITE, APPEND, CONTROL), Set.of()));
    }

    @Test
    void controlIsIndependentOfWrite() {
        // Control does not imply anything, and nothing implies it.
        assertEquals(EnumSet.of(CONTROL), eff(Set.of(CONTROL), Set.of()));
    }

    @Test
    void ofRoundTripsEveryMode() {
        for (AccessMode m : AccessMode.values()) {
            assertSame(m, AccessMode.of(m.uri()), m + " must round-trip through its ACL URI");
        }
        assertNull(AccessMode.of("http://www.w3.org/ns/auth/acl#NotAMode"));
        assertNull(AccessMode.of(null == null ? "urn:nope" : null));
    }
}
