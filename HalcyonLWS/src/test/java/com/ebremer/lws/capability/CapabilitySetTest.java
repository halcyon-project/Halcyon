package com.ebremer.lws.capability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.store.LwsResource;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

/**
 * {@link CapabilitySet} routing: candidate selection is first-match by registration order over the
 * installed {@link ResourceCapability}s, and consults {@link ResourceCapability#handles} only — so
 * the request never has to be a real one here, and {@code claims}/{@code serve} are never touched.
 */
class CapabilitySetTest {

    /** Reports a fixed {@code handles()} answer; every other method is an error if reached. */
    private static final class Stub implements ResourceCapability {
        private final boolean handles;

        Stub(boolean handles) {
            this.handles = handles;
        }

        @Override
        public boolean handles(HttpServletRequest req) {
            return handles;
        }

        @Override
        public boolean claims(LwsResource resource, HttpServletRequest req) {
            throw new AssertionError("claims() must not be reached during candidate selection");
        }

        @Override
        public void serve(CapabilityRequest cr) {
            throw new AssertionError("serve() must not be reached during candidate selection");
        }

        @Override
        public CapabilityDescriptor descriptor(LwsStorageConfig cfg) {
            return null;
        }
    }

    @Test
    void emptySetHasNoCandidate() {
        assertNull(CapabilitySet.EMPTY.candidate(null));
        assertFalse(CapabilitySet.EMPTY.hasResourceCapabilities());
    }

    @Test
    void candidateIsFirstHandlingCapabilityInRegistrationOrder() {
        Stub first = new Stub(true);
        Stub second = new Stub(true);
        assertSame(first, CapabilitySet.of(first, second).candidate(null));
    }

    @Test
    void candidateSkipsNonHandlingCapabilities() {
        Stub skip = new Stub(false);
        Stub take = new Stub(true);
        assertSame(take, CapabilitySet.of(skip, take).candidate(null));
    }

    @Test
    void candidateIsNullWhenNoneHandles() {
        CapabilitySet set = CapabilitySet.of(new Stub(false), new Stub(false));
        assertNull(set.candidate(null));
        assertTrue(set.hasResourceCapabilities());
    }
}
