package com.ebremer.lws.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Content negotiation and media-type helpers — {@link MediaTypes} (L3, and the H2/M2 helpers). */
class MediaTypesTest {

    @Test
    void admitsIsLenientOnAbsentOrWildcardAccept() {
        assertTrue(MediaTypes.admits(null, MediaTypes.LINKSET_JSON));
        assertTrue(MediaTypes.admits("", MediaTypes.LINKSET_JSON));
        assertTrue(MediaTypes.admits("*/*", MediaTypes.LINKSET_JSON));
        assertTrue(MediaTypes.admits("text/html, */*;q=0.1", MediaTypes.LINKSET_JSON));
    }

    @Test
    void admitsOnTheTypeFamilyOrTheExactType() {
        assertTrue(MediaTypes.admits("application/*", MediaTypes.LINKSET_JSON));
        assertTrue(MediaTypes.admits("application/linkset+json", MediaTypes.LINKSET_JSON));
        assertTrue(MediaTypes.admits("application/linkset+json; q=1", MediaTypes.LINKSET_JSON));
        assertTrue(MediaTypes.admits("text/*", MediaTypes.TURTLE));
        assertTrue(MediaTypes.admits("text/turtle", MediaTypes.TURTLE));
    }

    @Test
    void admitsRejectsASpecificIncompatibleAccept() {
        assertFalse(MediaTypes.admits("text/html", MediaTypes.LINKSET_JSON));
        assertFalse(MediaTypes.admits("application/xml", MediaTypes.LINKSET_JSON));
        // application/json is NOT application/linkset+json — a client asking for one is not asking
        // for the other.
        assertFalse(MediaTypes.admits("application/json", MediaTypes.LINKSET_JSON));
        assertFalse(MediaTypes.admits("application/json", MediaTypes.TURTLE));
        assertFalse(MediaTypes.admits("text/html", MediaTypes.TURTLE));
    }

    @Test
    void isJsonRecognisesTheStructuredSuffix() {
        assertTrue(MediaTypes.isJson("application/json"));
        assertTrue(MediaTypes.isJson("application/ld+json"));
        assertTrue(MediaTypes.isJson("application/lws+json"));
        assertTrue(MediaTypes.isJson("application/merge-patch+json"));
        assertTrue(MediaTypes.isJson("application/geo+json"));
        assertTrue(MediaTypes.isJson("application/json; charset=utf-8"), "parameters are ignored");
    }

    @Test
    void isJsonRejectsNonJson() {
        assertFalse(MediaTypes.isJson("image/tiff"));
        assertFalse(MediaTypes.isJson("text/plain"));
        assertFalse(MediaTypes.isJson("application/octet-stream"));
        assertFalse(MediaTypes.isJson(null));
    }

    @Test
    void bareStripsParameters() {
        assertEquals("application/json", MediaTypes.bare("application/json; charset=utf-8"));
        assertEquals("application/json", MediaTypes.bare("application/json"));
        assertEquals("text/turtle", MediaTypes.bare("  text/turtle ; q=1  "));
        assertNull(MediaTypes.bare(null));
    }

    @Test
    void admitsLwsJson() {
        assertTrue(MediaTypes.admitsLwsJson(null));
        assertTrue(MediaTypes.admitsLwsJson("*/*"));
        assertTrue(MediaTypes.admitsLwsJson("application/lws+json"));
        assertTrue(MediaTypes.admitsLwsJson("application/ld+json"));
        assertTrue(MediaTypes.admitsLwsJson("application/json"));
        assertFalse(MediaTypes.admitsLwsJson("text/html"));
    }

    @Test
    void negotiatePicksTheLabelButNeverInventsOne() {
        assertEquals(MediaTypes.LWS_JSON, MediaTypes.negotiate(null));
        assertEquals(MediaTypes.LWS_JSON, MediaTypes.negotiate("application/lws+json"));
        assertEquals(MediaTypes.LD_JSON, MediaTypes.negotiate("application/ld+json"));
        assertEquals(MediaTypes.JSON, MediaTypes.negotiate("application/json"));
        assertEquals(MediaTypes.LWS_JSON, MediaTypes.negotiate("text/nonsense"));
    }
}
