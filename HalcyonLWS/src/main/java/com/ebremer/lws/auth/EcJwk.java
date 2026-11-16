package com.ebremer.lws.auth;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

/**
 * A P-256 public key as a JWK (RFC 7517) and as its thumbprint (RFC 7638).
 *
 * <p>Two keys in this module are published for someone else to verify with: the webhook signing
 * key, which a subscriber uses to check an HTTP Message Signature, and the access-token signing
 * key, which anything validating a token this server issued fetches from the authorization
 * server's {@code jwks_uri}. Both need the same two things, and getting either subtly wrong —
 * a thumbprint over a non-canonical JWK, a coordinate carrying BigInteger's sign byte — produces
 * a key id that identifies nothing or a key that will not verify.
 *
 * <p>Hand-rolled rather than taken from a JOSE library because it is ten lines of arithmetic and
 * the alternative is a second JWK vocabulary in a module that already has one JWT library for
 * consuming tokens.
 */
public final class EcJwk {

    private EcJwk() {
    }

    /**
     * The key id: the RFC 7638 thumbprint of the public key.
     *
     * <p>Stable and meaningful — a hash of the key itself, so the same key always yields the same
     * id and a client can confirm the id names the key it holds. It also means rotation cannot
     * silently reuse an id.
     */
    public static String thumbprint(ECPublicKey pk) {
        // Canonical JWK per RFC 7638: required members only, lexicographic order, no whitespace.
        String canonical = "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"" + x(pk)
                + "\",\"y\":\"" + y(pk) + "\"}";
        return b64(sha256(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    /** The public key as a JWK, labelled with {@code kid} and the algorithm it is used with. */
    public static JsonObject publicJwk(ECPublicKey pk, String kid, String use) {
        return Json.createObjectBuilder()
                .add("kid", kid)
                .add("kty", "EC")
                .add("crv", "P-256")
                .add("alg", "ES256")
                .add("use", use)
                .add("x", x(pk))
                .add("y", y(pk))
                .build();
    }

    private static String x(ECPublicKey pk) {
        return b64(unsigned(pk.getW().getAffineX().toByteArray()));
    }

    private static String y(ECPublicKey pk) {
        return b64(unsigned(pk.getW().getAffineY().toByteArray()));
    }

    private static byte[] sha256(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Drop the sign byte BigInteger prepends, and left-pad to the P-256 field size. */
    private static byte[] unsigned(byte[] b) {
        int len = 32;
        if (b.length == len) {
            return b;
        }
        byte[] out = new byte[len];
        if (b.length > len) {
            System.arraycopy(b, b.length - len, out, 0, len);
        } else {
            System.arraycopy(b, 0, out, len - b.length, b.length);
        }
        return out;
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
