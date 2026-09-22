package com.ebremer.lws.search;

import com.ebremer.lws.http.Problem;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Seals and opens the opaque tokens this module puts in pagination URIs.
 *
 * <p>Two kinds of state travel in a page link — a {@link Cursor}'s resume key and, for a Type
 * Search, the filter the search was run with — and both are HMAC-sealed with one key
 * {@linkplain #init persisted in the store}. The spec requires page URIs to be opaque and tells
 * clients not to construct them; the signature is what makes that a guarantee rather than a
 * request, and it is also what lets the server keep <em>no</em> per-search state: everything
 * needed to serve page 2 is in the link, and a link nobody signed is not honoured.
 *
 * <p>A token that fails verification is answered 404 — the status lws10-index requires for a
 * pagination reference the server does not recognise. It is not a 400: the client did nothing
 * malformed, it presented a reference this server will not honour, and the remedy the spec gives
 * is to restart the query.
 */
final class Tokens {

    private static volatile byte[] key;

    private Tokens() {
    }

    /**
     * Prime the HMAC key from the store, once, at startup — outside any request transaction.
     *
     * <p>Deliberately eager rather than lazy: opening a token runs inside the read transaction a
     * pagination request holds, and a first-use lazy load could need a <em>write</em> transaction
     * to mint the key, which cannot be opened inside that read. Idempotent — every storage calls
     * it and they share one persisted key.
     */
    static void init(com.ebremer.lws.store.LwsStore store) {
        key = com.ebremer.lws.store.SecretStore.secret(store, "cursor-hmac", 32);
    }

    private static byte[] key() {
        byte[] k = key;
        if (k == null) {
            // A caller that never ran init() (a test, say). Safe only outside a transaction — see
            // init(). In the running server init() has always run first, so this is never reached.
            synchronized (Tokens.class) {
                if (key == null) {
                    key = com.ebremer.lws.store.SecretStore.secret(
                            com.ebremer.lws.store.LwsStore.get(), "cursor-hmac", 32);
                }
                k = key;
            }
        }
        return k;
    }

    /** {@code base64url(payload).base64url(hmac)}. */
    static String seal(String payload) {
        String b64 = b64(payload.getBytes(StandardCharsets.UTF_8));
        return b64 + "." + b64(hmac(b64));
    }

    /** The payload of a token this server sealed, or a 404 if it did not seal it. */
    static String open(String token) {
        int dot = token.lastIndexOf('.');
        if (dot < 0) {
            throw unrecognised();
        }
        String b64 = token.substring(0, dot);
        byte[] sig;
        byte[] payload;
        try {
            sig = Base64.getUrlDecoder().decode(token.substring(dot + 1));
            payload = Base64.getUrlDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw unrecognised();
        }
        if (!java.security.MessageDigest.isEqual(sig, hmac(b64))) {
            throw unrecognised();
        }
        return new String(payload, StandardCharsets.UTF_8);
    }

    static Problem unrecognised() {
        return Problem.notFound("this pagination reference is not recognised; restart the query");
    }

    private static byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key(), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
