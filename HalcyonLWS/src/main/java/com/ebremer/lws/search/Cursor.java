package com.ebremer.lws.search;

import com.ebremer.lws.http.Problem;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * An opaque, unforgeable pagination cursor.
 *
 * <p><strong>Keyset, not offset.</strong> A cursor carries the resume key of the last item already
 * <em>scanned</em>, and the next page is "everything after that". Offsets break under concurrent
 * mutation — an insert on page 1 pushes an item onto page 2, where a client paging forward sees it
 * twice, and a delete makes it skip one entirely. Seeking on a monotonic key cannot do either: an
 * insert always lands beyond the cursor, and a delete merely makes a page short. Containers and Type
 * Search key on a numeric sequence ({@link #at}); the Type Index keys on the type URI itself.
 *
 * <p><strong>Scanned, not emitted.</strong> Authorization filtering removes members <em>after</em>
 * the store hands them back, so a cursor keyed on the last item the client actually saw would rescan
 * the filtered-out ones forever — or, worse, skip live ones. The high-water mark has to be what the
 * server looked at, not what it chose to show.
 *
 * <p><strong>Signed.</strong> The payload is HMAC-sealed with a key {@linkplain #init persisted in
 * the store}. A cursor is meant to be opaque, and clients are told not to construct one; the
 * signature is what makes that a guarantee rather than a request. It also binds the cursor to the
 * filter it came from, so page 2 of one search cannot be replayed against a different one.
 *
 * <p>The key used to be minted at class load, which rotated it on every restart and 404ed every
 * outstanding cursor for no reason. It now comes from {@link SecretStore}, generated once and kept.
 * (M3.)
 *
 * <p>Because the whole state is in the cursor, there is no server-side session to expire — which is
 * why a valid cursor never goes stale, and only a forged or corrupt one is refused.
 */
public record Cursor(String collection, String filterHash, String after) {

    private static volatile byte[] key;

    /**
     * A cursor for a collection keyed on a monotonic sequence — containers and Type Search.
     *
     * <p>The resume key is opaque to this record; a numeric collection stores it as its decimal
     * string. The Type Index instead keys on the type URI itself and passes that string directly.
     */
    public static Cursor at(String collection, String filterHash, long afterSeq) {
        return new Cursor(collection, filterHash, Long.toString(afterSeq));
    }

    /** The resume key as a sequence number, or {@code -1} when there is none (or it is not numeric). */
    public long afterSeq() {
        try {
            return Long.parseLong(after);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /**
     * Prime the HMAC key from the store, once, at startup — outside any request transaction.
     *
     * <p>Deliberately eager rather than lazy: {@code decode()} runs inside the read transaction a
     * pagination request holds, and a first-use lazy load could need a <em>write</em> transaction
     * to mint the key, which cannot be opened inside that read. Priming here means the key is
     * already in hand by the time any cursor is encoded or decoded. Idempotent — the two storages
     * both call it and get the same persisted key.
     */
    public static void init(com.ebremer.lws.store.LwsStore store) {
        key = com.ebremer.lws.store.SecretStore.secret(store, "cursor-hmac", 32);
    }

    private static byte[] key() {
        byte[] k = key;
        if (k == null) {
            // A caller that never ran init() (a test, say). Safe only outside a transaction — see
            // init(). In the running server init() has always run first, so this is never reached.
            synchronized (Cursor.class) {
                if (key == null) {
                    key = com.ebremer.lws.store.SecretStore.secret(
                            com.ebremer.lws.store.LwsStore.get(), "cursor-hmac", 32);
                }
                k = key;
            }
        }
        return k;
    }

    /** Encode and sign. */
    public String encode() {
        // The resume key comes first, space-delimited from the collection URI and the filter hash.
        // All three are single tokens — a resource URI, a decimal number, or a hash — never a value
        // containing a space, so a three-way split reconstructs them exactly.
        String payload = after + " " + collection + " " + filterHash;
        String b64 = b64(payload.getBytes(StandardCharsets.UTF_8));
        return b64 + "." + b64(hmac(b64));
    }

    /**
     * Decode and verify.
     *
     * <p>A cursor that fails verification, or was minted for a different collection or a different
     * filter, is one this server does not recognise — which the search spec says MUST be a 404 or
     * 410. It is not a 400: the client did nothing malformed, it presented a reference the server
     * will not honour.
     */
    public static Cursor decode(String s, String collection, String filterHash) {
        if (s == null || s.isBlank()) {
            return new Cursor(collection, filterHash, "");
        }
        int dot = s.lastIndexOf('.');
        if (dot < 0) {
            throw unrecognised();
        }
        String b64 = s.substring(0, dot);
        byte[] sig;
        byte[] payload;
        try {
            sig = Base64.getUrlDecoder().decode(s.substring(dot + 1));
            payload = Base64.getUrlDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw unrecognised();
        }
        if (!java.security.MessageDigest.isEqual(sig, hmac(b64))) {
            throw unrecognised();
        }
        String[] parts = new String(payload, StandardCharsets.UTF_8).split(" ", 3);
        if (parts.length != 3) {
            throw unrecognised();
        }
        // parts[0] is the resume key, left as an opaque string — a decimal for a seq-keyed
        // collection, a type URI for the Type Index. The HMAC above is what guards it against
        // forgery; there is nothing more to validate about its shape here.
        if (!parts[1].equals(collection) || !parts[2].equals(filterHash)) {
            // Replaying a cursor against a different collection or filter would page through a
            // result set the client never ran.
            throw unrecognised();
        }
        return new Cursor(collection, filterHash, parts[0]);
    }

    private static Problem unrecognised() {
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
