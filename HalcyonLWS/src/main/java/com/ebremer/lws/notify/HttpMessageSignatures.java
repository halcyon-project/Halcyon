package com.ebremer.lws.notify;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.List;

/**
 * Signs outbound webhook deliveries with HTTP Message Signatures (RFC 9421).
 *
 * <p>The problem this solves: a webhook is an unauthenticated POST arriving at an inbox
 * from somewhere on the internet. Without a signature, anything that learns an inbox URL
 * can forge notifications from this storage — inventing resources, inventing deletions.
 * The signature is what makes a notification an assertion by the storage rather than a
 * claim by whoever connected.
 *
 * <p>The signature base covers {@code @method}, {@code @scheme}, {@code @authority},
 * {@code @path}, {@code content-type} and {@code content-digest} — the last of which is
 * what binds the signature to the <em>body</em>. Signing only the headers would leave the
 * payload swappable under a valid signature.
 *
 * <p>The verifying key is published in the storage description as a
 * {@code verificationMethod}, so a subscriber can find it by dereferencing the storage
 * identifier it was told about, with nothing hardcoded.
 *
 * <p>The keypair used to be generated at class load, so every restart rotated it: the published
 * {@code verificationMethod} changed and any signature a subscriber had cached stopped verifying.
 * It is now {@linkplain #init persisted in the store} and stable across restarts. (M3.)
 */
public final class HttpMessageSignatures {

    private static volatile KeyPair keys;
    private static volatile String keyId;

    private HttpMessageSignatures() {
    }

    /**
     * Prime the signing key from the store, once, at startup. Idempotent — the two storages both
     * call it and get the same persisted key, which is correct: they are one server and advertise
     * one verification key.
     */
    public static void init(com.ebremer.lws.store.LwsStore store) {
        keys = com.ebremer.lws.store.SecretStore.ecKeyPair(store, "webhook-signing");
        keyId = computeKeyId(keys);
    }

    private static KeyPair keys() {
        KeyPair k = keys;
        if (k == null) {
            synchronized (HttpMessageSignatures.class) {
                if (keys == null) {
                    keys = com.ebremer.lws.store.SecretStore.ecKeyPair(
                            com.ebremer.lws.store.LwsStore.get(), "webhook-signing");
                    keyId = computeKeyId(keys);
                }
                k = keys;
            }
        }
        return k;
    }

    public static String keyId() {
        keys();
        return keyId;
    }

    /**
     * The key id, as the RFC 7638 JWK thumbprint of the public key.
     *
     * <p>Stable and meaningful — it is a hash of the key itself, so the same key always yields the
     * same id and a client can confirm the id names the key it holds. The old id was
     * {@code System.identityHashCode}, which was neither: it changed every run and identified
     * nothing.
     */
    private static String computeKeyId(KeyPair kp) {
        ECPublicKey pk = (ECPublicKey) kp.getPublic();
        String x = b64(unsigned(pk.getW().getAffineX().toByteArray()));
        String y = b64(unsigned(pk.getW().getAffineY().toByteArray()));
        // Canonical JWK per RFC 7638: required members only, lexicographic order, no whitespace.
        String canonical = "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"" + x + "\",\"y\":\"" + y + "\"}";
        return b64(sha256(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    /** The public key as a JWK, for the storage description's {@code verificationMethod}. */
    public static JsonObject publicJwk() {
        ECPublicKey pk = (ECPublicKey) keys().getPublic();
        byte[] x = unsigned(pk.getW().getAffineX().toByteArray());
        byte[] y = unsigned(pk.getW().getAffineY().toByteArray());
        return Json.createObjectBuilder()
                .add("kid", keyId())
                .add("kty", "EC")
                .add("crv", "P-256")
                .add("alg", "ES256")
                .add("x", b64(x))
                .add("y", b64(y))
                .build();
    }

    /** A signed request's headers, ready to send. */
    public record Signed(String contentDigest, String signatureInput, String signature) {
    }

    /**
     * Sign a delivery.
     *
     * @param created seconds since the epoch, covered by the signature so a subscriber
     *                can reject a replayed one outside its clock-skew window
     */
    public static Signed sign(String method, URI target, String contentType, byte[] body,
            long created) {
        String digest = "sha-256=:" + b64pad(sha256(body)) + ":";

        // The covered components, in the order they appear in the base. Order is part of
        // the signature: a verifier reconstructs the base from @signature-params, so a
        // different order is a different signature.
        List<String> components = List.of(
                "\"@method\"", "\"@scheme\"", "\"@authority\"", "\"@path\"",
                "\"content-type\"", "\"content-digest\"");
        String params = "(" + String.join(" ", components) + ")"
                + ";created=" + created
                + ";keyid=\"" + keyId() + "\""
                + ";alg=\"ecdsa-p256-sha256\"";

        String scheme = target.getScheme();
        String authority = target.getAuthority();
        String path = target.getRawPath() == null || target.getRawPath().isEmpty()
                ? "/" : target.getRawPath();

        StringBuilder base = new StringBuilder();
        base.append("\"@method\": ").append(method).append('\n');
        base.append("\"@scheme\": ").append(scheme).append('\n');
        base.append("\"@authority\": ").append(authority).append('\n');
        base.append("\"@path\": ").append(path).append('\n');
        base.append("\"content-type\": ").append(contentType).append('\n');
        base.append("\"content-digest\": ").append(digest).append('\n');
        base.append("\"@signature-params\": ").append(params);

        byte[] sig = sign(base.toString().getBytes(StandardCharsets.UTF_8));
        return new Signed(
                digest,
                "sig1=" + params,
                "sig1=:" + b64pad(sig) + ":");
    }

    /**
     * ES256 produces a raw {@code r||s} pair, not the DER sequence
     * {@code SHA256withECDSA} emits by default. The JDK spells that
     * {@code inP1363Format}.
     */
    private static byte[] sign(byte[] data) {
        try {
            Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
            s.initSign(keys().getPrivate());
            s.update(data);
            return s.sign();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
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

    private static String b64pad(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }
}
