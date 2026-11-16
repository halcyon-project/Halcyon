package com.ebremer.lws.notify;

import com.ebremer.lws.auth.EcJwk;
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

    /**
     * The bare key id: the RFC 7638 thumbprint, which is what the published JWK carries as its
     * {@code kid} and what the fragment of {@link #verificationMethodId} is.
     */
    public static String keyId() {
        keys();
        return keyId;
    }

    /**
     * The {@code keyid} a signature carries, and the {@code id} of the verification method the
     * storage description publishes: {@code {storage}#{thumbprint}}.
     *
     * <p>lws10-notifications-webhook requires the {@code keyid} to be "a URL with a fragment
     * component", because that is what makes a signature self-describing: a receiver strips the
     * fragment to get the storage identifier, dereferences it for the storage description,
     * confirms the description's {@code id} matches, and finds the verification method whose
     * {@code id} is either the whole keyid or just its fragment. A bare thumbprint — which is
     * what this used to emit — gives a receiver nowhere to start.
     */
    public static String verificationMethodId(String storageUri) {
        return storageUri + "#" + keyId();
    }

    /**
     * The key id, as the RFC 7638 JWK thumbprint of the public key. The old id was
     * {@code System.identityHashCode}, which changed every run and identified nothing.
     */
    private static String computeKeyId(KeyPair kp) {
        return EcJwk.thumbprint((ECPublicKey) kp.getPublic());
    }

    /** The public key as a JWK, for the storage description's {@code verificationMethod}. */
    public static JsonObject publicJwk() {
        return EcJwk.publicJwk((ECPublicKey) keys().getPublic(), keyId(), "sig");
    }

    /** A signed request's headers, ready to send. */
    public record Signed(String contentDigest, String signatureInput, String signature) {
    }

    /**
     * Sign a delivery.
     *
     * @param storageUri the canonical URI of the storage this delivery is about, which the
     *                   {@code keyid} is built from so a receiver can resolve the key from the
     *                   signature alone
     * @param created seconds since the epoch, covered by the signature so a subscriber
     *                can reject a replayed one outside its clock-skew window
     */
    public static Signed sign(String storageUri, String method, URI target, String contentType,
            byte[] body, long created) {
        String digest = "sha-256=:" + b64pad(sha256(body)) + ":";

        // The covered components, in the order they appear in the base. Order is part of
        // the signature: a verifier reconstructs the base from @signature-params, so a
        // different order is a different signature.
        List<String> components = List.of(
                "\"@method\"", "\"@scheme\"", "\"@authority\"", "\"@path\"",
                "\"content-type\"", "\"content-digest\"");
        String params = "(" + String.join(" ", components) + ")"
                + ";created=" + created
                + ";keyid=\"" + verificationMethodId(storageUri) + "\""
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


    private static String b64pad(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }
}
