package com.ebremer.lws.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.lws.vocab.LWSX;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Files dropped onto the disk mirror under names its URIs reserve are left alone. The case that
 * found it: a slide archive keeps a sidecar {@code scan.svs.meta} beside every {@code scan.svs}, and
 * the reconciler adopted both — the sidecar listed in its container, its URI answering with the
 * linkset of {@code scan.svs}, and its description written into the named graph that IS that linkset.
 *
 * <p>Over a real temp-dir mirror and a real (temp) TDB2 store, as {@code MirrorMoveTest}.
 */
class MirrorAuxiliaryNamesTest {

    private static final String SITE = "https://localhost:8888";
    private static final String ROOT = SITE + "/images/tcga/";
    private static final String OWNER = "https://alice.example/#me";

    private Path dir;
    private Path disk;
    private LwsStore store;
    private LwsStorageConfig cfg;
    private MirrorContentStore mirror;

    @BeforeEach
    void open() throws Exception {
        dir = Files.createTempDirectory("mirror-aux");
        disk = dir.resolve("content");
        Files.createDirectories(disk);
        Constructor<LwsStore> ctor = LwsStore.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        store = ctor.newInstance(dir.resolve("tdb2").toString());
        cfg = new LwsStorageConfig("/images/tcga", disk, NamingPolicyType.SLUG, SITE);
        mirror = new MirrorContentStore(disk);
        store.write(() -> new ResourceRegistry(store, cfg).seedRoot());

        drop("brca/scan.txt", "the slide");
        drop("brca/scan.txt.meta", "scan.txt https://example.org/source/scan.txt");
        drop("brca/scan.txt.acr", "not an access control resource");
        drop("report.meta", "a root-level file named like a linkset");
        drop(".description", "not the storage description");
        drop(".iiif", "not the IIIF endpoint");
        drop(".notifications/subscriptions/abc", "not a subscription");
        drop("collections.meta/inner.txt", "a directory may end in .meta: its URI ends in a slash");
        drop("manifest.csv", "an ordinary name");
    }

    @AfterEach
    void close() {
        try {
            store.raw().close();
        } catch (RuntimeException ignore) {
            // best effort
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignore) {
            // a leftover temp dir is harmless
        }
    }

    @Test
    void filesNamedLikeAuxiliaryResourcesOrEndpointsAreNotAdopted() {
        reconciler().reconcile();

        for (String rel : List.of("brca/scan.txt.meta", "brca/scan.txt.acr", "report.meta",
                ".description", ".iiif", ".notifications/subscriptions/abc")) {
            assertFalse(registered(ROOT + rel), rel + " must not be adopted");
            assertTrue(Files.isRegularFile(disk.resolve(rel)), rel + " stays on disk, untouched");
        }
        for (String uri : List.of("brca/", "brca/scan.txt", "collections.meta/",
                "collections.meta/inner.txt", "manifest.csv", ".notifications/",
                ".notifications/subscriptions/")) {
            assertTrue(registered(ROOT + uri), uri + " is adopted as before");
        }
    }

    @Test
    void theSidecarDoesNotLandInItsPrimarysLinksetGraph() {
        reconciler().reconcile();

        String linkset = ROOT + "brca/scan.txt" + LwsStorageConfig.LINKSET_SUFFIX;
        assertTrue(store.read(() -> store.raw().getNamedModel(linkset).isEmpty()),
                "scan.txt's linkset holds nothing the reconciler put there");
        assertFalse(store.read(() -> store.raw().getNamedModel(ROOT + "brca/").listObjects().toList()
                .stream().anyMatch(o -> o.isURIResource() && o.asResource().getURI().equals(linkset))),
                "and the container lists no member at the linkset's URI");
    }

    @Test
    void aSecondPassFindsNothingToDo() {
        reconciler().reconcile();
        int after = resourceCount();
        reconciler().reconcile();
        assertEquals(after, resourceCount(), "the skipped files are not retried as new each pass");
        assertEquals(8, after, "root + brca/ + scan.txt + collections.meta/ + inner.txt + manifest.csv"
                + " + .notifications/ + .notifications/subscriptions/");
    }

    private void drop(String rel, String content) throws Exception {
        Path p = disk.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    private MirrorReconciler reconciler() {
        return new MirrorReconciler(store, cfg, mirror, mirror, OWNER);
    }

    private boolean registered(String uri) {
        return store.read(() -> new ResourceRegistry(store, cfg).exists(uri));
    }

    private int resourceCount() {
        return store.read(() -> store.system().listSubjectsWithProperty(LWSX.storage).toList().size());
    }
}
