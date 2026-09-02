package com.ebremer.lws.store;

import com.ebremer.lws.config.LwsMount;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the multi-disk mount mapping of the mirror storage: keys resolve to
 * their owning root by LONGEST prefix, segment-wise (a mount at {@code tcga}
 * never claims {@code tcga2}); walks map disk paths back to the same keys;
 * shadowed paths belong to the deeper root; escapes are refused per root; and
 * a mount point's directory is never deleted with its container.
 */
class MountTableTest {

    private static MountTable table(Path main, Path tcga, Path brca) {
        return new MountTable(main, List.of(
                new LwsMount("tcga", tcga),
                new LwsMount("tcga/brca", brca)));
    }

    @Test
    void keysResolveToTheirOwningRootByLongestPrefix(@TempDir Path dir) {
        Path main = dir.resolve("main");
        Path tcga = dir.resolve("disk2");
        Path brca = dir.resolve("disk3");
        MountTable t = table(main, tcga, brca);

        assertEquals(main.resolve("plain.txt").normalize(), t.resolve("plain.txt"));
        assertEquals(tcga.toAbsolutePath().normalize(), t.resolve("tcga"),
                "the mount point itself is the mount root");
        assertEquals(tcga.resolve("coad/slide.svs").normalize(), t.resolve("tcga/coad/slide.svs"));
        assertEquals(brca.resolve("x.h5").normalize(), t.resolve("tcga/brca/x.h5"),
                "the deeper mount wins its subtree");
        assertEquals(main.resolve("tcga2/f.txt").normalize(), t.resolve("tcga2/f.txt"),
                "prefix matching is segment-wise, tcga2 is not tcga");
    }

    @Test
    void ownershipIsSegmentWiseAndNested(@TempDir Path dir) {
        MountTable t = table(dir.resolve("m"), dir.resolve("d2"), dir.resolve("d3"));
        assertEquals("", t.ownerPrefix("plain.txt"));
        assertEquals("", t.ownerPrefix("tcga2/f.txt"));
        assertEquals("tcga", t.ownerPrefix("tcga"));
        assertEquals("tcga", t.ownerPrefix("tcga/coad/x.svs"));
        assertEquals("tcga/brca", t.ownerPrefix("tcga/brca"));
        assertEquals("tcga/brca", t.ownerPrefix("tcga/brca/deep/x.h5"));
        assertTrue(t.isMountPoint("tcga"));
        assertTrue(t.isMountPoint("tcga/brca"));
        assertFalse(t.isMountPoint("tcga/coad"));
    }

    @Test
    void escapesAreRefusedAgainstTheOwningRoot(@TempDir Path dir) {
        MountTable t = table(dir.resolve("m"), dir.resolve("d2"), dir.resolve("d3"));
        assertThrows(IllegalArgumentException.class, () -> t.resolve("../outside"));
        assertThrows(IllegalArgumentException.class, () -> t.resolve("tcga/../../outside"));
    }

    @Test
    void duplicateMountPrefixesAreRejected(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class, () -> new MountTable(dir, List.of(
                new LwsMount("tcga", dir.resolve("a")),
                new LwsMount("tcga/", dir.resolve("b")))));
    }

    @Test
    void walkRootsMapDiskPathsBackToTheSameKeys(@TempDir Path dir) {
        MountTable t = table(dir.resolve("m"), dir.resolve("d2"), dir.resolve("d3"));
        List<MountTable.WalkRoot> roots = t.walkRoots();
        assertEquals(3, roots.size());
        assertEquals("", roots.get(0).prefix());

        for (MountTable.WalkRoot wr : roots) {
            String rel = "sub/file.bin";
            String key = wr.keyOf(rel);
            assertEquals(wr.root().resolve(rel).normalize(), t.resolve(key),
                    "walk key must resolve back to the walked path for " + wr.prefix());
        }
        assertEquals("tcga", roots.get(1).keyOf(""),
                "the mount root itself is the mount-point container");
    }

    @Test
    void mirrorStoreWritesLandOnTheMountedDisk(@TempDir Path dir) throws Exception {
        Path main = Files.createDirectories(dir.resolve("main"));
        Path other = Files.createDirectories(dir.resolve("otherdisk"));
        MirrorContentStore store = new MirrorContentStore(main,
                List.of(new LwsMount("tcga", other)));

        store.writeAt("tcga/coad/slide.txt",
                new ByteArrayInputStream("bytes".getBytes(StandardCharsets.UTF_8)));
        assertTrue(Files.exists(other.resolve("coad/slide.txt")),
                "the blob must land under the mount root");
        assertFalse(Files.exists(main.resolve("tcga")),
                "nothing must be created under the main root for a mounted key");

        store.writeAt("plain.txt",
                new ByteArrayInputStream("main".getBytes(StandardCharsets.UTF_8)));
        assertTrue(Files.exists(main.resolve("plain.txt")));

        // Deleting the mount-point container must not delete the disk's directory.
        store.removeDir("tcga");
        assertTrue(Files.isDirectory(other), "a mount root is never removed");
    }
}
