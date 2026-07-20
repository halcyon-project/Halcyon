package com.ebremer.lws.store;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cache decorator's contract: pathFor materializes exactly once and answers locally after
 * that; reads prefer the cache but never populate it; delete evicts; the sweep prunes what is
 * no longer referenced and leaves the rest; only opaque keys and key-minting remotes are legal.
 */
class MaterializedContentStoreTest {

    /** A remote key-minting store: blobs in a map, reads counted, pathFor unsupported. */
    static final class FakeRemote implements ContentStore {

        final Map<String, byte[]> blobs = new HashMap<>();
        final AtomicInteger reads = new AtomicInteger();
        int minted = 0;

        private static String k(String key, String ext) {
            return key + (ext == null ? "" : ext);
        }

        @Override
        public Path root() {
            throw new UnsupportedOperationException("remote store has no local root");
        }

        @Override
        public Path pathFor(String key, String ext) {
            throw new UnsupportedOperationException("remote store has no local paths");
        }

        @Override
        public Written write(InputStream in, String ext) throws IOException {
            String key = String.format("fake%028d", ++minted);
            byte[] bytes = in.readAllBytes();
            blobs.put(k(key, ext), bytes);
            return new Written(key, bytes.length, "cafe");
        }

        @Override
        public InputStream read(String key, String ext) throws IOException {
            reads.incrementAndGet();
            byte[] b = blobs.get(k(key, ext));
            if (b == null) {
                throw new NoSuchFileException("no remote blob " + k(key, ext));
            }
            return new ByteArrayInputStream(b);
        }

        @Override
        public long size(String key, String ext) throws IOException {
            byte[] b = blobs.get(k(key, ext));
            if (b == null) {
                throw new NoSuchFileException("no remote blob " + k(key, ext));
            }
            return b.length;
        }

        @Override
        public boolean exists(String key, String ext) {
            return blobs.containsKey(k(key, ext));
        }

        @Override
        public boolean delete(String key, String ext) {
            blobs.remove(k(key, ext));
            return true;
        }

        @Override
        public int sweepOrphans(Predicate<String> isReferenced, long graceMillis) {
            int n = 0;
            var it = blobs.keySet().iterator();
            while (it.hasNext()) {
                String composite = it.next();
                int dot = composite.indexOf('.');
                String key = dot < 0 ? composite : composite.substring(0, dot);
                if (!isReferenced.test(key)) {
                    it.remove();
                    n++;
                }
            }
            return n;
        }
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void materializesOnceThenAnswersFromCache(@TempDir Path cache) throws IOException {
        FakeRemote remote = new FakeRemote();
        remote.blobs.put("abcd1234.ttl", bytes("hello"));
        MaterializedContentStore store = new MaterializedContentStore(remote, cache);

        Path p = store.pathFor("abcd1234", ".ttl");
        assertTrue(Files.exists(p));
        assertArrayEquals(bytes("hello"), Files.readAllBytes(p));
        assertEquals(cache.resolve("ab").resolve("cd").resolve("abcd1234.ttl"), p);
        assertEquals(1, remote.reads.get());

        assertEquals(p, store.pathFor("abcd1234", ".ttl"));
        assertEquals(1, remote.reads.get(), "second pathFor must not touch the remote");
    }

    @Test
    void readPrefersCacheButNeverPopulatesIt(@TempDir Path cache) throws IOException {
        FakeRemote remote = new FakeRemote();
        remote.blobs.put("abcd1234.ttl", bytes("hello"));
        MaterializedContentStore store = new MaterializedContentStore(remote, cache);

        // Not cached: read streams from the remote and leaves no file behind.
        try (InputStream in = store.read("abcd1234", ".ttl")) {
            assertArrayEquals(bytes("hello"), in.readAllBytes());
        }
        assertEquals(1, remote.reads.get());
        assertFalse(Files.exists(cache.resolve("ab").resolve("cd").resolve("abcd1234.ttl")));

        // Cached (via pathFor): read serves the local copy without a remote call.
        store.pathFor("abcd1234", ".ttl");
        assertEquals(2, remote.reads.get());
        try (InputStream in = store.read("abcd1234", ".ttl")) {
            assertArrayEquals(bytes("hello"), in.readAllBytes());
        }
        assertEquals(2, remote.reads.get(), "cached read must not touch the remote");
    }

    @Test
    void sizeAndExistsPreferCacheAndFallThrough(@TempDir Path cache) throws IOException {
        FakeRemote remote = new FakeRemote();
        remote.blobs.put("abcd1234.bin", bytes("12345"));
        MaterializedContentStore store = new MaterializedContentStore(remote, cache);

        assertTrue(store.exists("abcd1234", ".bin"));
        assertEquals(5, store.size("abcd1234", ".bin"));
        assertFalse(store.exists("eeee0000", ".bin"));

        store.pathFor("abcd1234", ".bin");
        remote.blobs.clear();
        assertTrue(store.exists("abcd1234", ".bin"), "cached copy answers even if remote listing fails");
        assertEquals(5, store.size("abcd1234", ".bin"));
    }

    @Test
    void writeDelegatesToRemote(@TempDir Path cache) throws IOException {
        FakeRemote remote = new FakeRemote();
        MaterializedContentStore store = new MaterializedContentStore(remote, cache);

        ContentStore.Written w = store.write(new ByteArrayInputStream(bytes("payload")), ".bin");
        assertEquals(7, w.size());
        assertTrue(remote.exists(w.key(), ".bin"));
    }

    @Test
    void deleteEvictsTheLocalCopy(@TempDir Path cache) throws IOException {
        FakeRemote remote = new FakeRemote();
        remote.blobs.put("abcd1234.bin", bytes("x"));
        MaterializedContentStore store = new MaterializedContentStore(remote, cache);

        Path p = store.pathFor("abcd1234", ".bin");
        assertTrue(Files.exists(p));
        assertTrue(store.delete("abcd1234", ".bin"));
        assertFalse(Files.exists(p));
        assertFalse(remote.exists("abcd1234", ".bin"));
    }

    @Test
    void sweepPrunesUnreferencedCacheEntriesAndDelegates(@TempDir Path cache) throws IOException {
        FakeRemote remote = new FakeRemote();
        remote.blobs.put("aaaa1111.bin", bytes("live"));
        remote.blobs.put("bbbb2222.bin", bytes("dead"));
        MaterializedContentStore store = new MaterializedContentStore(remote, cache);

        Path live = store.pathFor("aaaa1111", ".bin");
        Path dead = store.pathFor("bbbb2222", ".bin");
        // Old enough to be past any grace period.
        FileTime old = FileTime.fromMillis(System.currentTimeMillis() - 60_000);
        Files.setLastModifiedTime(live, old);
        Files.setLastModifiedTime(dead, old);

        int reaped = store.sweepOrphans("aaaa1111"::equals, 5_000);
        assertEquals(1, reaped, "remote sweep reclaimed the unreferenced blob");
        assertTrue(Files.exists(live), "referenced cache entry stays");
        assertFalse(Files.exists(dead), "unreferenced cache entry is pruned");
        assertTrue(remote.exists("aaaa1111", ".bin"));
        assertFalse(remote.exists("bbbb2222", ".bin"));
    }

    @Test
    void sweepLeavesFreshEntriesWithinGrace(@TempDir Path cache) throws IOException {
        FakeRemote remote = new FakeRemote();
        remote.blobs.put("bbbb2222.bin", bytes("dead"));
        MaterializedContentStore store = new MaterializedContentStore(remote, cache);

        Path dead = store.pathFor("bbbb2222", ".bin");
        store.sweepOrphans(k -> false, 60_000);
        assertTrue(Files.exists(dead), "a just-written cache entry survives the grace period");
    }

    @Test
    void rejectsNonOpaqueKeys(@TempDir Path cache) {
        MaterializedContentStore store = new MaterializedContentStore(new FakeRemote(), cache);
        assertThrows(IllegalArgumentException.class, () -> store.pathFor("../evil", null));
        assertThrows(IllegalArgumentException.class, () -> store.pathFor("a/b", null));
        assertThrows(IllegalArgumentException.class, () -> store.pathFor("ab", null));
        assertThrows(IllegalArgumentException.class, () -> store.exists("a.b.c", null));
    }

    @Test
    void refusesToWrapAPathKeyedStore(@TempDir Path cache) {
        assertThrows(IllegalArgumentException.class,
                () -> new MaterializedContentStore(new MirrorContentStore(cache), cache));
    }

    @Test
    void missingRemoteBlobSurfacesAsUncheckedIO(@TempDir Path cache) {
        MaterializedContentStore store = new MaterializedContentStore(new FakeRemote(), cache);
        assertThrows(UncheckedIOException.class, () -> store.pathFor("dddd4444", ".bin"));
        assertFalse(Files.exists(cache.resolve("dd").resolve("dd").resolve("dddd4444.bin")),
                "a failed materialization leaves nothing behind");
    }
}
