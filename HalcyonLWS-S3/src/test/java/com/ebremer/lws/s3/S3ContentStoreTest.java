package com.ebremer.lws.s3;

import com.ebremer.lws.store.ContentStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The store's own logic — key minting, spool-and-hash writes, prefix layout, the list-based
 * sweep — against the in-memory bucket. The AWS adapter is not under test here.
 */
class S3ContentStoreTest {

    private static final String SHA256_HELLO =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void writeSpoolsHashesAndUploads(@TempDir Path root) throws IOException {
        InMemoryS3Blobs mem = new InMemoryS3Blobs();
        S3ContentStore store = new S3ContentStore(mem, null, root);

        ContentStore.Written w = store.write(new ByteArrayInputStream(bytes("hello")), ".bin");

        assertTrue(w.key().matches("[0-9a-f]{32}"), "opaque UUID-hex key: " + w.key());
        assertEquals(5, w.size());
        assertEquals(SHA256_HELLO, w.sha256());
        assertTrue(mem.exists(w.key() + ".bin"));
        try (InputStream in = store.read(w.key(), ".bin")) {
            assertArrayEquals(bytes("hello"), in.readAllBytes());
        }
        try (var spooled = Files.list(root.resolve(".spool"))) {
            assertEquals(0, spooled.count(), "spool file cleaned after upload");
        }
    }

    @Test
    void prefixIsNormalisedAndApplied(@TempDir Path root) throws IOException {
        InMemoryS3Blobs mem = new InMemoryS3Blobs();
        S3ContentStore store = new S3ContentStore(mem, "/pods/main/", root);

        ContentStore.Written w = store.write(new ByteArrayInputStream(bytes("x")), ".ttl");
        assertTrue(mem.exists("pods/main/" + w.key() + ".ttl"),
                "object keyed under the normalised prefix");
        assertTrue(store.exists(w.key(), ".ttl"));
        assertEquals(1, store.size(w.key(), ".ttl"));
    }

    @Test
    void missingObjectSurfacesAsNoSuchFile(@TempDir Path root) {
        S3ContentStore store = new S3ContentStore(new InMemoryS3Blobs(), "", root);
        assertThrows(NoSuchFileException.class, () -> store.read("f00df00df00df00d", ".bin"));
        assertThrows(NoSuchFileException.class, () -> store.size("f00df00df00df00d", ".bin"));
        assertFalse(store.exists("f00df00df00df00d", ".bin"));
    }

    @Test
    void deleteIsIdempotent(@TempDir Path root) throws IOException {
        InMemoryS3Blobs mem = new InMemoryS3Blobs();
        S3ContentStore store = new S3ContentStore(mem, "", root);
        ContentStore.Written w = store.write(new ByteArrayInputStream(bytes("x")), ".bin");

        assertTrue(store.delete(w.key(), ".bin"));
        assertFalse(store.exists(w.key(), ".bin"));
        assertTrue(store.delete(w.key(), ".bin"), "deleting the absent is success");
    }

    @Test
    void pathForRefusesBareUse(@TempDir Path root) {
        S3ContentStore store = new S3ContentStore(new InMemoryS3Blobs(), "", root);
        assertThrows(UnsupportedOperationException.class, () -> store.pathFor("abcd1234", ".bin"));
    }

    @Test
    void sweepReapsOnlyUnreferencedPastGraceInsideTheFlatLayout(@TempDir Path root) {
        InMemoryS3Blobs mem = new InMemoryS3Blobs();
        S3ContentStore store = new S3ContentStore(mem, "pods", root);
        Instant old = Instant.now().minusSeconds(3600);

        mem.putDirect("pods/aaaa1111.bin", bytes("live"), old);
        mem.putDirect("pods/bbbb2222.bin", bytes("orphan, old"), old);
        mem.putDirect("pods/cccc3333.bin", bytes("orphan, fresh"), Instant.now());
        mem.putDirect("pods/nested/dddd4444.bin", bytes("foreign"), old);
        mem.putDirect("elsewhere/eeee5555.bin", bytes("outside prefix"), old);

        int reaped = store.sweepOrphans("aaaa1111"::equals, 60_000);

        assertEquals(1, reaped);
        assertTrue(mem.exists("pods/aaaa1111.bin"), "referenced stays");
        assertFalse(mem.exists("pods/bbbb2222.bin"), "old orphan reaped");
        assertTrue(mem.exists("pods/cccc3333.bin"), "fresh orphan survives the grace period");
        assertTrue(mem.exists("pods/nested/dddd4444.bin"), "nested keys are not this store's");
        assertTrue(mem.exists("elsewhere/eeee5555.bin"), "keys outside the prefix are untouched");
    }

    @Test
    void sweepCleansAbandonedSpoolFiles(@TempDir Path root) throws IOException {
        S3ContentStore store = new S3ContentStore(new InMemoryS3Blobs(), "", root);
        Path spool = root.resolve(".spool");
        Files.createDirectories(spool);
        Path stale = spool.resolve(".tmp-deadbeef");
        Files.write(stale, bytes("crashed mid-upload"));
        Files.setLastModifiedTime(stale,
                java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(3600)));

        store.sweepOrphans(k -> true, 60_000);
        assertFalse(Files.exists(stale), "abandoned spool file reaped");
    }
}
