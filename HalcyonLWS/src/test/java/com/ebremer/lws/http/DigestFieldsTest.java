package com.ebremer.lws.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/**
 * RFC 9530 digest-field formatting, {@code Want-*} algorithm selection, and inbound
 * {@code Content-Digest} verification — both the in-memory and the streamed-upload paths.
 *
 * @author Erich Bremer
 */
class DigestFieldsTest {

    private static final byte[] HELLO = "hello".getBytes(StandardCharsets.UTF_8);

    private static String sha256Hex(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    @Test
    void formatsAndVerifiesInMemory() {
        String header = DigestFields.format("sha-256", HELLO);
        assertTrue(header.startsWith("sha-256=:") && header.endsWith(":"), header);
        DigestFields.verify(header, HELLO); // matches -> no throw
        assertThrows(Problem.class,
                () -> DigestFields.verify(header, "world".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void sha256FromHexEqualsFormat() throws Exception {
        assertEquals(DigestFields.format("sha-256", HELLO), DigestFields.sha256FromHex(sha256Hex(HELLO)));
    }

    @Test
    void choosesPreferredAvailableAlgorithm() {
        // In-memory representations can answer either algorithm; the higher weight wins.
        assertEquals("sha-512",
                DigestFields.chooseAlgorithm("sha-256=3, sha-512=10", DigestFields.SUPPORTED_SET).orElseThrow());
        // A stored data resource can only cheaply answer sha-256, whatever the client prefers.
        assertEquals("sha-256",
                DigestFields.chooseAlgorithm("sha-256=3, sha-512=10", DigestFields.STORED_SET).orElseThrow());
        assertTrue(DigestFields.chooseAlgorithm("md5=10", DigestFields.SUPPORTED_SET).isEmpty()); // unsupported
        assertTrue(DigestFields.chooseAlgorithm("sha-256=0", DigestFields.SUPPORTED_SET).isEmpty()); // weight 0
        assertTrue(DigestFields.chooseAlgorithm(null, DigestFields.SUPPORTED_SET).isEmpty());
    }

    @Test
    void ignoresUnsupportedAndRejectsMalformed() {
        DigestFields.verify(null, HELLO);           // absent -> ok
        DigestFields.verify("md5=:abcd:", HELLO);   // unsupported alg -> ignored, not verified
        assertThrows(Problem.class,
                () -> DigestFields.verify("sha-256=:not valid base64!:", HELLO));
    }

    @Test
    void verifyStreamedChecksSha256FromHashAndSha512ByRehash() throws Exception {
        String hex = sha256Hex(HELLO);
        // sha-256 is checked against the streamed hash: the content is NEVER re-opened.
        DigestFields.verifyStreamed(DigestFields.format("sha-256", HELLO), hex,
                () -> { throw new AssertionError("must not re-read the blob for a sha-256 digest"); });
        // sha-512 is checked by re-hashing the stored content.
        DigestFields.verifyStreamed(DigestFields.format("sha-512", HELLO), hex,
                () -> new ByteArrayInputStream(HELLO));
        // A mismatching sha-256 is a 400, decided from the streamed hash alone.
        String wrongHex = sha256Hex("world".getBytes(StandardCharsets.UTF_8));
        assertThrows(Problem.class, () -> DigestFields.verifyStreamed(
                DigestFields.format("sha-256", HELLO), wrongHex, () -> new ByteArrayInputStream(HELLO)));
    }
}
