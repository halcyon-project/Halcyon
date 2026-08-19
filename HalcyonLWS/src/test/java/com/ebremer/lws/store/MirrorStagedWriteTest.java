package com.ebremer.lws.store;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mirror store's write is staged, and that is what makes a refused write survivable.
 *
 * <p>Its key IS the resource's path, so writing the new bytes and keeping the old ones used to
 * be the same act: an upload landed on the live file before the transaction that authorizes it
 * ran, and a transaction that then refused the write — a missing {@code If-Match} answered 428,
 * say — rolled back by deleting the file outright. The resource the client was refused
 * permission to replace was gone, and its registered metadata pointed at nothing.
 *
 * <p>So these tests are about the file at the key, not about the staging mechanics: after a
 * staged write that is never published, the original content must still be there, byte for byte.
 */
class MirrorStagedWriteTest {

    private static final byte[] ORIGINAL = "original content".getBytes(StandardCharsets.UTF_8);
    private static final byte[] REPLACEMENT = "overwrite attempt".getBytes(StandardCharsets.UTF_8);

    private static MirrorContentStore store(Path root) {
        return new MirrorContentStore(root, List.of());
    }

    private static ByteArrayInputStream in(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    @Test
    void stagedBytesAreNotVisibleAtTheKeyUntilPublished(@TempDir Path root) throws Exception {
        MirrorContentStore store = store(root);
        store.writeAt("note.txt", in(ORIGINAL));

        try (PathKeyedStore.Staged staged = store.stageAt("note.txt", in(REPLACEMENT))) {
            assertArrayEqualsFile(ORIGINAL, root.resolve("note.txt"),
                    "the live file must not change while a write is only staged");
            assertEquals(REPLACEMENT.length, staged.written().size());

            staged.publish();
            assertArrayEqualsFile(REPLACEMENT, root.resolve("note.txt"),
                    "publish replaces the file");
        }
    }

    @Test
    void anUnpublishedWriteLeavesTheOriginalIntact(@TempDir Path root) throws Exception {
        MirrorContentStore store = store(root);
        store.writeAt("note.txt", in(ORIGINAL));

        // Exactly the servlet's refused-PUT path: stage the upload, then abandon it because the
        // transaction threw (428/412/403) instead of committing.
        try (PathKeyedStore.Staged staged = store.stageAt("note.txt", in(REPLACEMENT))) {
            // no publish
        }

        assertArrayEqualsFile(ORIGINAL, root.resolve("note.txt"),
                "a refused write must leave the resource exactly as it was");
        assertTrue(store.exists("note.txt", null), "and must not delete it either");
        assertEquals(1, filesIn(root), "the staged bytes are discarded, not left beside it");
    }

    @Test
    void stagingCreatesNothingAtTheKeyForANewResource(@TempDir Path root) throws Exception {
        MirrorContentStore store = store(root);

        try (PathKeyedStore.Staged staged = store.stageAt("deep/nested/new.txt", in(ORIGINAL))) {
            assertFalse(Files.exists(root.resolve("deep/nested/new.txt")),
                    "a create that is refused must not leave a file behind");
            staged.publish();
        }

        assertArrayEqualsFile(ORIGINAL, root.resolve("deep/nested/new.txt"),
                "parent directories are made on the way, as before");
    }

    @Test
    void closeAfterPublishIsHarmless(@TempDir Path root) throws Exception {
        MirrorContentStore store = store(root);
        PathKeyedStore.Staged staged = store.stageAt("note.txt", in(ORIGINAL));
        staged.publish();
        staged.close();

        assertArrayEqualsFile(ORIGINAL, root.resolve("note.txt"),
                "close is the discard path only for bytes that were never published");
    }

    @Test
    void writeAtStillStagesAndPublishesInOneStep(@TempDir Path root) throws Exception {
        MirrorContentStore store = store(root);
        ContentStore.Written w = store.writeAt("note.txt", in(ORIGINAL));

        assertEquals("note.txt", w.key());
        assertEquals(ORIGINAL.length, w.size());
        assertArrayEqualsFile(ORIGINAL, root.resolve("note.txt"), "writeAt publishes");
        assertEquals(1, filesIn(root), "and leaves no staging file behind");
    }

    private static void assertArrayEqualsFile(byte[] expected, Path file, String why)
            throws Exception {
        assertTrue(Files.exists(file), why);
        assertEquals(new String(expected, StandardCharsets.UTF_8),
                Files.readString(file, StandardCharsets.UTF_8), why);
    }

    private static long filesIn(Path dir) throws Exception {
        try (var s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).count();
        }
    }
}
