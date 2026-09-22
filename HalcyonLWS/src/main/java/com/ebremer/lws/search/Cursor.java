package com.ebremer.lws.search;

import com.ebremer.lws.http.Problem;

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
 * <p><strong>Signed.</strong> The payload is HMAC-sealed by {@link Tokens}. A cursor is meant to be
 * opaque, and clients are told not to construct one; the signature is what makes that a guarantee
 * rather than a request. It also binds the cursor to the filter it came from, so page 2 of one
 * search cannot be replayed against a different one.
 *
 * <p>Because the whole state is in the cursor, there is no server-side session to expire — which is
 * why a valid cursor never goes stale, and only a forged or corrupt one is refused.
 */
public record Cursor(String collection, String filterHash, String after) {

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
     * Prime the signing key from the store, once, at startup. Kept here as well as on
     * {@link Tokens} because the storages have always called {@code Cursor.init}.
     */
    public static void init(com.ebremer.lws.store.LwsStore store) {
        Tokens.init(store);
    }

    /** Encode and sign. */
    public String encode() {
        // The resume key comes first, space-delimited from the collection URI and the filter hash.
        // All three are single tokens — a resource URI, a decimal number, or a hash — never a value
        // containing a space, so a three-way split reconstructs them exactly.
        return Tokens.seal(after + " " + collection + " " + filterHash);
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
        String[] parts = Tokens.open(s).split(" ", 3);
        if (parts.length != 3) {
            throw unrecognised();
        }
        // parts[0] is the resume key, left as an opaque string — a decimal for a seq-keyed
        // collection, a type URI for the Type Index. The HMAC is what guards it against forgery;
        // there is nothing more to validate about its shape here.
        if (!parts[1].equals(collection) || !parts[2].equals(filterHash)) {
            // Replaying a cursor against a different collection or filter would page through a
            // result set the client never ran.
            throw unrecognised();
        }
        return new Cursor(collection, filterHash, parts[0]);
    }

    private static Problem unrecognised() {
        return Tokens.unrecognised();
    }
}
