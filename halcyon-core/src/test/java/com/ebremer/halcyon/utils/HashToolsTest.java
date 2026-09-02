package com.ebremer.halcyon.utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D5 — the digest helpers.
 * <p>
 * The only member of the D5 list whose class is actually LIVE ({@code
 * DirectoryProcessor}, {@code RasterToPolygon} and {@code ImageMeta} all call
 * it), which is why these fixes got tests and the dead-island ones got a much
 * shorter leash. The live callers use the file-based entry points, so the two
 * bugs below were dead methods inside a live class — reachable the moment
 * anyone hashed a string or a buffer.
 *
 * @author erich
 */
class HashToolsTest {

    /** Bytes that differ between UTF-8 and the Windows-1252/Latin-1 defaults. */
    private static final String NON_ASCII = "café-Ω-é";

    @Test
    @DisplayName("string hashes are UTF-8, not the platform default charset")
    void stringHashesAreUtf8() {
        // The bug: getBytes() with no argument encodes with the JVM's default
        // charset, so the SAME string hashed to a DIFFERENT digest on the
        // Windows dev box (windows-1252) than in the UTF-8 container. A digest
        // that is not stable across hosts cannot do the one job it has.
        assertEquals(HashTools.MD5(NON_ASCII.getBytes(StandardCharsets.UTF_8)),
                     HashTools.MD5(NON_ASCII));
        assertEquals(HashTools.SHA512(NON_ASCII.getBytes(StandardCharsets.UTF_8)),
                     HashTools.SHA512(NON_ASCII));
    }

    @Test
    @DisplayName("the UTF-8 digest is pinned to a known constant")
    void knownAnswer() {
        // Pins the encoding itself: were this to silently revert to a Latin-1
        // default, the assertion above would still pass on a Latin-1 JVM.
        assertEquals("900150983cd24fb0d6963f7d28e17f72", HashTools.MD5("abc"));
    }

    @Test
    @DisplayName("a ByteBuffer hashes its position..limit window, not the whole array")
    void byteBufferHonoursWindow() {
        // The bug: src.array() returned the ENTIRE backing array, so a slice or a
        // partially-filled read buffer hashed the padding too.
        byte[] backing = "XXhelloYYYY".getBytes(StandardCharsets.UTF_8);
        ByteBuffer window = ByteBuffer.wrap(backing);
        window.position(2).limit(7);   // exactly "hello"

        assertEquals(HashTools.MD5("hello"), HashTools.MD5(window));

        // Control: this is what array() did, and it is a different digest.
        assertNotEquals(HashTools.MD5(backing), HashTools.MD5(window));
    }

    @Test
    @DisplayName("hashing a buffer does not consume the caller's position")
    void byteBufferIsNotConsumed() {
        // MessageDigest.update(ByteBuffer) drains to the limit; the duplicate() in
        // hash() is what keeps this a pure function. Without it, a caller that
        // hashed then read would silently get nothing.
        ByteBuffer buf = ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8));
        HashTools.MD5(buf);
        assertEquals(0, buf.position(), "buffer position was consumed");
        assertEquals(5, buf.remaining(), "buffer was drained");
        // Still hashes the same the second time — the real cost of consuming it.
        assertEquals(HashTools.MD5("hello"), HashTools.MD5(buf));
    }

    @Test
    @DisplayName("a direct buffer hashes instead of throwing")
    void directBuffer() {
        // A direct buffer has no accessible backing array, so array() threw
        // UnsupportedOperationException outright.
        ByteBuffer direct = ByteBuffer.allocateDirect(5);
        direct.put("hello".getBytes(StandardCharsets.UTF_8)).flip();
        assertEquals(HashTools.MD5("hello"), HashTools.MD5(direct));
    }

    @Test
    @DisplayName("a read-only buffer hashes instead of throwing")
    void readOnlyBuffer() {
        // array() throws ReadOnlyBufferException on these.
        ByteBuffer ro = ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)).asReadOnlyBuffer();
        assertEquals(HashTools.MD5("hello"), HashTools.MD5(ro));
        assertEquals(HashTools.SHA512("hello"), HashTools.SHA512(ro));
    }

    @Test
    @DisplayName("digests are fixed-width lowercase hex")
    void hexFormatting() {
        // The shared hex() helper: a byte < 0x10 must keep its leading zero, or
        // the digest silently shortens and two inputs can collide in the string.
        assertEquals(32, HashTools.MD5("abc").length());
        assertEquals(128, HashTools.SHA512("abc").length());
        assertEquals(HashTools.MD5("abc"), HashTools.MD5("abc").toLowerCase());
    }
}
