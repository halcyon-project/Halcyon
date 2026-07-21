package com.ebremer.lws.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link PresentedToken}: it decodes the routing claims from a JWS without verifying it, and
 * treats the scheme/emptiness as request errors but a non-JWS body as simply unroutable.
 */
class PresentedTokenTest {

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    /** header.payload.signature, with an arbitrary (unverified) signature segment. */
    private static String jwt(String header, String payload) {
        return b64(header) + "." + b64(payload) + ".c2ln";
    }

    @Test
    void decodesRoutingClaimsFromABearerJwt() {
        String token = jwt("{\"alg\":\"RS256\",\"kid\":\"key-1\"}",
                "{\"iss\":\"https://issuer.example\",\"sub\":\"https://alice.example/#me\"}");
        PresentedToken t = PresentedToken.parse("Bearer " + token);
        assertEquals(token, t.raw());
        assertEquals("https://issuer.example", t.iss());
        assertEquals("https://alice.example/#me", t.sub());
        assertEquals("key-1", t.kid());
    }

    @Test
    void schemeMatchIsCaseInsensitive() {
        String token = jwt("{\"kid\":\"k\"}", "{\"iss\":\"i\"}");
        assertEquals("i", PresentedToken.parse("bearer " + token).iss());
    }

    @Test
    void nonBearerSchemeIsInvalidRequest() {
        InvalidBearerTokenException e = assertThrows(InvalidBearerTokenException.class,
                () -> PresentedToken.parse("Basic dXNlcjpwYXNz"));
        assertEquals("invalid_request", e.error());
    }

    @Test
    void emptyTokenIsInvalidRequest() {
        InvalidBearerTokenException e = assertThrows(InvalidBearerTokenException.class,
                () -> PresentedToken.parse("Bearer    "));
        assertEquals("invalid_request", e.error());
    }

    @Test
    void anOpaqueTokenLeavesRoutingClaimsNullButKeepsTheRawToken() {
        PresentedToken t = PresentedToken.parse("Bearer opaque-token");
        assertEquals("opaque-token", t.raw());
        assertNull(t.iss());
        assertNull(t.sub());
        assertNull(t.kid());
    }

    @Test
    void anUndecodablePayloadIsToleratedNotThrown() {
        PresentedToken t = PresentedToken.parse("Bearer aaa.@@@.bbb");
        assertNull(t.iss());
        assertNull(t.sub());
    }
}
