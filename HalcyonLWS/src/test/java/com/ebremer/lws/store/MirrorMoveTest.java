package com.ebremer.lws.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.acp.AccessMode;
import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.acp.AcrStore;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.config.LwsMount;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.lws.http.MoveHandler;
import com.ebremer.lws.http.Problem;
import com.ebremer.lws.notify.Notifications;
import com.ebremer.lws.search.Cursor;
import com.ebremer.lws.search.LwsQuery;
import com.ebremer.lws.search.SearchService;
import com.ebremer.lws.vocab.ACP;
import com.ebremer.lws.vocab.LWS;
import com.ebremer.lws.vocab.LWSX;
import jakarta.json.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A move on the disk mirror, where the URI is the path and the disk is authoritative, so a moved
 * resource cannot keep its identifier (lws10-core, Moving Resources: {@code "preservesIdentifier":
 * false}). Pins that the rename on disk and the rewrite of everything keyed by the URI happen
 * together or not at all, that the reconciler and the watcher — which trust the disk — leave a moved
 * resource where the move put it instead of de-registering it and adopting it afresh, and that the
 * access control, subscriptions and search index follow it.
 *
 * <p>Over a real temp-dir mirror, a real (temp) TDB2 store, the real ACP engine, and, where the
 * watcher is the point, the real watcher.
 */
class MirrorMoveTest {

    private static final String SITE = "https://localhost:8888";
    private static final String ROOT = SITE + "/W3ClwsSlash/";
    private static final String SRC = ROOT + "src/";
    private static final String DST = ROOT + "dst/";
    private static final String FILE = SRC + "a.txt";
    private static final String SUB = SRC + "sub/";
    private static final String INNER = SUB + "inner.txt";
    private static final String DEEP = SUB + "deep/";
    private static final String ALICE = "https://alice.example/#me";
    private static final String BOB = "https://bob.example/#me";
    private static final String PERSON = "https://schema.org/Person";
    private static final String LICENSE = "https://creativecommons.org/licenses/by/4.0/";

    private Path dir;
    private Path disk;
    private LwsStore store;
    private LwsStorageConfig cfg;
    private MirrorContentStore mirror;
    private Notifications notify;
    private MoveHandler moves;

    @BeforeEach
    void open() throws Exception {
        dir = Files.createTempDirectory("mirror-move");
        disk = dir.resolve("content");
        Files.createDirectories(disk);
        Constructor<LwsStore> ctor = LwsStore.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        store = ctor.newInstance(dir.resolve("tdb2").toString());
        cfg = new LwsStorageConfig("/W3ClwsSlash", disk, NamingPolicyType.SLUG, SITE);
        mirror = new MirrorContentStore(disk);
        notify = new Notifications(store, cfg);
        moves = new MoveHandler(store, cfg, mirror, notify, EnumSet.allOf(ResourceType.class));

        // root ─┬─ src/ ─┬─ a.txt
        //       │        └─ sub/ ─┬─ inner.txt
        //       │                 └─ deep/
        //       └─ dst/
        store.write(() -> {
            new ResourceRegistry(store, cfg).seedRoot();
            Model acp = store.acp();
            Resource acr = acp.createResource("urn:test:root-acr");
            Resource ac = acp.createResource("urn:test:root-acr#ac");
            Resource policy = acp.createResource("urn:test:root-acr#policy");
            Resource matcher = acp.createResource("urn:test:root-acr#alice");
            acr.addProperty(RDF.type, ACP.AccessControlResource);
            acr.addProperty(ACP.resource, acp.createResource(ROOT));
            acr.addProperty(ACP.accessControl, ac);
            acr.addProperty(ACP.memberAccessControl, ac);
            ac.addProperty(ACP.apply, policy);
            policy.addProperty(RDF.type, ACP.Policy);
            policy.addProperty(ACP.allOf, matcher);
            for (AccessMode m : AccessMode.values()) {
                policy.addProperty(ACP.allow, m.iri());
            }
            matcher.addProperty(ACP.agent, acp.createResource(ALICE));
        });
        container(SRC);
        container(DST);
        file(FILE, "the content travels unchanged");
        container(SUB);
        file(INNER, "carried along");
        container(DEEP);

        store.write(() -> {
            new ResourceRegistry(store, cfg).replaceDeclaredTypes(FILE, List.of(PERSON));
            LinksetStore.replace(store, FILE, Map.of("license", List.of(LICENSE)));
            // Bob may read a.txt and inner.txt by their own ACRs, nothing else.
            AcrStore.replace(store, FILE, readerAcr(FILE, BOB));
            AcrStore.replace(store, INNER, readerAcr(INNER, BOB));
        });
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

    // --- The rename ---------------------------------------------------------

    @Test
    void theMirrorDescribesMovesThatDoNotPreserveIdentifiers() {
        JsonObject props = moves.descriptor().capability().properties();
        assertFalse(props.getBoolean("preservesIdentifier"),
                "the URI is the path on disk, so a moved resource takes a new one");
    }

    @Test
    void aDataResourceIsRenamedUnderTheDestinationWithEverythingBoundToIt() throws Exception {
        MoveHandler.Moved moved = move(FILE, DST);

        String now = DST + "a.txt";
        assertTrue(moved.renamed());
        assertEquals(now, moved.uri());
        assertFalse(Files.exists(disk.resolve("src/a.txt")));
        assertEquals("the content travels unchanged",
                Files.readString(disk.resolve("dst/a.txt")), "renamed, not copied");
        assertMovedFile(now);
    }

    @Test
    void aContainerIsRenamedWithItsWholeSubtree() throws Exception {
        MoveHandler.Moved moved = move(SUB, DST);

        String sub = DST + "sub/";
        assertEquals(sub, moved.uri());
        assertTrue(Files.isDirectory(disk.resolve("dst/sub/deep")));
        assertEquals("carried along", Files.readString(disk.resolve("dst/sub/inner.txt")));
        assertFalse(Files.exists(disk.resolve("src/sub")));
        store.read(() -> {
            ResourceRegistry reg = registry();
            assertEquals(DST, reg.find(sub).orElseThrow().parent());
            assertEquals(sub, reg.find(sub + "inner.txt").orElseThrow().parent(),
                    "a member's parent follows its renamed container");
            assertEquals(sub, reg.find(sub + "deep/").orElseThrow().parent());
            assertEquals(List.of(sub + "inner.txt", sub + "deep/"), uris(reg.childRefs(sub)));
            assertEquals("dst/sub/inner.txt", reg.find(sub + "inner.txt").orElseThrow().storageKey());
            for (String old : List.of(SUB, INNER, DEEP)) {
                assertFalse(reg.exists(old), old);
                assertTrue(store.raw().getNamedModel(old).isEmpty(), "no graph left at " + old);
            }
            assertEquals(List.of(FILE), uris(reg.childRefs(SRC)));
            assertTrue(new AcpEngine(store).allows(new AgentContext(BOB, null, null, List.of()),
                    sub + "inner.txt", AccessMode.READ), "a descendant's own ACR follows it too");
        });
    }

    @Test
    void aTakenNameIsDisambiguatedAsATakenSlugIs() throws Exception {
        file(DST + "a.txt", "already here");
        // Only on disk, not yet adopted: still taken, as a two URIs must never share a file.
        Files.writeString(disk.resolve("dst/a-1.txt"), "dropped in");

        MoveHandler.Moved moved = move(FILE, DST);

        assertEquals(DST + "a-2.txt", moved.uri());
        assertEquals("already here", Files.readString(disk.resolve("dst/a.txt")));
        assertEquals("dropped in", Files.readString(disk.resolve("dst/a-1.txt")));
        assertMovedFile(DST + "a-2.txt");
    }

    @Test
    void aMoveTheDiskLayoutCannotHoldIsRefusedAndChangesNothing() throws Exception {
        Path other = Files.createDirectories(dir.resolve("other"));
        Path deepDisk = Files.createDirectories(dir.resolve("deep-disk"));
        MirrorContentStore mounted = new MirrorContentStore(disk, List.of(
                new LwsMount("m", other), new LwsMount("src/sub/deep", deepDisk)));
        LwsStorageConfig mcfg = new LwsStorageConfig("/W3ClwsSlash", disk, NamingPolicyType.SLUG,
                SITE, List.of(new LwsMount("m", other), new LwsMount("src/sub/deep", deepDisk)));
        MoveHandler h = new MoveHandler(store, mcfg, mounted, notify, EnumSet.allOf(ResourceType.class));
        container(ROOT + "m/");

        for (String[] c : List.of(
                new String[] {FILE, ROOT + "m/"},     // onto another disk
                new String[] {SRC, DST},              // holds a mount point
                new String[] {DEEP, DST})) {          // is one
            Problem p = assertThrows(Problem.class, () -> h.write(() -> h.move(
                    registry(c[0]), List.of(c[1]), c[0] + ".meta", all(), null)));
            assertEquals(409, p.status(), c[0] + " -> " + c[1] + ": " + p.getMessage());
        }
        assertTrue(Files.exists(disk.resolve("src/a.txt")));
        store.read(() -> assertEquals(SRC, registry().find(FILE).orElseThrow().parent()));
    }

    @Test
    void aMoveThatDoesNotCommitPutsTheDiskBack() {
        RuntimeException boom = assertThrows(IllegalStateException.class, () -> moves.write(() -> {
            moves.move(registry(SUB), List.of(DST), SUB + ".meta", all(), null);
            throw new IllegalStateException("the transaction fails after the rename");
        }));
        assertNotNull(boom);
        assertTrue(Files.exists(disk.resolve("src/sub/inner.txt")), "the rename was undone");
        assertFalse(Files.exists(disk.resolve("dst/sub")));
        store.read(() -> {
            assertTrue(registry().exists(INNER));
            assertFalse(registry().exists(DST + "sub/inner.txt"));
            assertEquals(List.of(), uris(registry().childRefs(DST)));
        });
    }

    // --- The reconciler and the watcher ---------------------------------------

    @Test
    void aReconcileAfterAMoveFindsNothingToDo() {
        move(SUB, DST);
        int before = resourceCount();

        reconciler().reconcile();

        assertEquals(before, resourceCount(), "nothing de-registered, nothing adopted");
        assertMovedSubtree();
    }

    @Test
    void aSurveyFromBeforeTheMoveChangesNothingAppliedAfterIt() {
        MirrorReconciler r = reconciler();
        MirrorReconciler.Survey stale = r.survey();
        move(SUB, DST);

        r.apply(stale);

        assertMovedSubtree();
        store.read(() -> assertFalse(registry().exists(SUB), "the old place is not adopted again"));
    }

    @Test
    void aSurveyThatSawTheRenameBeforeTheCommitChangesNothing() throws Exception {
        // The disk renamed, the index not yet: what a reconcile sees while a move's transaction is
        // open. Applied once the move has committed, it must neither drop the old entries (they are
        // the moved resources) nor adopt the new paths (they are registered).
        Files.createDirectories(disk.resolve("dst"));
        Files.move(disk.resolve("src/sub"), disk.resolve("dst/sub"));
        MirrorReconciler r = reconciler();
        MirrorReconciler.Survey midMove = r.survey();
        assertFalse(midMove.gone().isEmpty(), "the survey did see the old entries gone");
        Files.move(disk.resolve("dst/sub"), disk.resolve("src/sub"));
        move(SUB, DST);

        r.apply(midMove);

        assertMovedSubtree();
    }

    @Test
    void theWatcherLeavesAMovedSubtreeWhereTheMovePutIt() throws Exception {
        MirrorWatcher watcher = new MirrorWatcher(store, cfg, mirror, mirror, ALICE);
        watcher.start();
        try {
            move(SUB, DST);
            // A file dropped in after the move: once it is adopted, a reconcile the move's own
            // events triggered has run (or was coalesced into this one) since the move.
            Files.writeString(disk.resolve("dst/probe"), "probe");
            Instant until = Instant.now().plusSeconds(30);
            while (!store.read(() -> registry().exists(DST + "probe"))) {
                assertTrue(Instant.now().isBefore(until), "the watcher never reconciled");
                Thread.sleep(200);
            }
        } finally {
            watcher.stop();
        }
        assertMovedSubtree();
        store.read(() -> {
            for (String old : List.of(SUB, INNER, DEEP)) {
                assertFalse(registry().exists(old), old + " came back");
            }
        });
    }

    // --- What follows the resource ---------------------------------------------

    @Test
    void theSearchIndexFindsItUnderItsNewUriOnly() {
        move(FILE, DST);
        LwsQuery people = new LwsQuery(Map.of(LwsQuery.TYPE, List.of(List.of(PERSON))));
        SearchService.Page page = new SearchService(store, cfg,
                new AgentContext(BOB, null, null, List.of()), new AcpEngine(store))
                .search(people, new Cursor(cfg.typeSearchUri(), people.fingerprint(), ""));
        assertEquals(1, page.total());
        assertEquals(DST + "a.txt", page.items().get(0).id());
    }

    @Test
    void aSubscriptionToTheMovedResourceFollowsItAndHearsTheMove() {
        store.write(() -> {
            Model g = store.raw().getNamedModel(LWSX.SUBSCRIPTION_GRAPH);
            Resource s = g.createResource("urn:lws:subscription:on-file");
            s.addProperty(RDF.type, LWS.WebhookSubscription);
            s.addProperty(LWSX.storage, g.createResource(ROOT));
            s.addProperty(LWS.subscription, "https://inbox.example/on-file");
            s.addProperty(LWSX.ownedBy, g.createResource(BOB));
            s.addProperty(LWS.topic, g.createResource(FILE));
        });

        MoveHandler.Moved moved = move(FILE, DST);

        assertEquals(1, moved.plan().size());
        JsonObject a = moved.plan().get(0).activities().get(0);
        assertEquals("Move", a.getJsonArray("type").getString(0));
        assertEquals(DST + "a.txt", a.getJsonObject("object").getString("id"));
        assertEquals(SRC, a.getString("origin"));
        assertEquals(DST, a.getString("target"));
        store.read(() -> assertTrue(store.raw().getNamedModel(LWSX.SUBSCRIPTION_GRAPH).contains(
                null, LWS.topic, store.raw().getDefaultModel().createResource(DST + "a.txt"))));
    }

    // --- Helpers ------------------------------------------------------------

    private MoveHandler.Moved move(String uri, String to) {
        return moves.write(() -> moves.move(registry(uri), List.of(to),
                uri + LwsStorageConfig.LINKSET_SUFFIX, all(), null));
    }

    private void assertMovedFile(String now) {
        store.read(() -> {
            ResourceRegistry reg = registry();
            assertFalse(reg.exists(FILE));
            LwsResource f = reg.find(now).orElseThrow();
            assertEquals(DST, f.parent());
            assertEquals(now.substring(ROOT.length()), f.storageKey(), "keyed by its new path");
            assertEquals(ALICE, f.ownedBy());
            assertTrue(f.extraTypes().contains(PERSON), "its types");
            assertEquals(Map.of("license", List.of(LICENSE)), LinksetStore.read(store, now),
                    "its links, anchored at the new URI");
            assertTrue(store.raw().getNamedModel(FILE + LwsStorageConfig.LINKSET_SUFFIX).isEmpty());
            assertTrue(AcrStore.read(store, FILE).isEmpty(), "no ACR left at the old URI");
            Model acr = AcrStore.read(store, now);
            assertTrue(acr.contains(acr.createResource(now + ".acr"), ACP.resource,
                    acr.createResource(now)), "its ACR, served at the new .acr");
            assertTrue(new AcpEngine(store).allows(new AgentContext(BOB, null, null, List.of()),
                    now, AccessMode.READ), "and still in force");
            assertTrue(uris(reg.childRefs(DST)).contains(now));
            assertFalse(uris(reg.childRefs(SRC)).contains(FILE));
            try (InputStream in = mirror.read(f.storageKey(), f.ext())) {
                assertEquals("the content travels unchanged",
                        new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
        });
    }

    private void assertMovedSubtree() {
        store.read(() -> {
            ResourceRegistry reg = registry();
            assertEquals(DST, reg.find(DST + "sub/").orElseThrow().parent());
            assertTrue(reg.exists(DST + "sub/inner.txt"));
            assertTrue(reg.exists(DST + "sub/deep/"));
            assertFalse(reg.exists(SUB));
            Model acr = AcrStore.read(store, DST + "sub/inner.txt");
            assertFalse(acr.isEmpty(), "the ACR of the moved member is intact");
            assertEquals(ALICE, reg.find(DST + "sub/inner.txt").orElseThrow().ownedBy());
        });
    }

    private MirrorReconciler reconciler() {
        return new MirrorReconciler(store, cfg, mirror, mirror, ALICE);
    }

    private int resourceCount() {
        return store.read(() -> store.system().listSubjectsWithProperty(LWSX.storage).toList().size());
    }

    private LwsResource registry(String uri) {
        return store.read(() -> registry().find(uri).orElseThrow());
    }

    private ResourceRegistry registry() {
        return new ResourceRegistry(store, cfg);
    }

    private static MoveHandler.Authority all() {
        return new MoveHandler.Authority(uri -> EnumSet.allOf(AccessMode.class),
                (mode, what) -> Problem.forbidden(what));
    }

    private static List<String> uris(List<ResourceRegistry.ChildRef> refs) {
        return refs.stream().map(ResourceRegistry.ChildRef::uri).toList();
    }

    private static String parentOf(String uri) {
        String u = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        return u.substring(0, u.lastIndexOf('/') + 1);
    }

    private static String keyOf(String uri) {
        String rel = uri.substring(ROOT.length());
        return rel.endsWith("/") ? rel.substring(0, rel.length() - 1) : rel;
    }

    /** A container as the mirror holds one: a directory, registered under its path. */
    private void container(String uri) throws Exception {
        Files.createDirectories(disk.resolve(keyOf(uri)));
        store.write(() -> {
            ResourceRegistry reg = registry();
            reg.create(new LwsResource(uri, ResourceType.CONTAINER, List.of(), null, 0, Instant.now(),
                    ResourceRegistry.containerEtag(0), keyOf(uri), null, parentOf(uri), reg.nextSeq(),
                    ALICE, ALICE, null), null);
        });
    }

    /** A data resource as the mirror holds one: a file, registered at its true size. */
    private void file(String uri, String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Files.write(disk.resolve(keyOf(uri)), bytes);
        store.write(() -> {
            ResourceRegistry reg = registry();
            reg.create(new LwsResource(uri, ResourceType.DATA_RESOURCE, List.of(), "text/plain",
                    bytes.length, Instant.now(), "\"d" + reg.nextSeq() + "\"", keyOf(uri), ".txt",
                    parentOf(uri), reg.nextSeq(), ALICE, ALICE, null), null);
        });
    }

    /** An ACR letting {@code agent} read {@code uri}, as a client would PUT it. */
    private static Model readerAcr(String uri, String agent) {
        String acr = uri + ".acr";
        String ttl = """
                @prefix acp: <http://www.w3.org/ns/solid/acp#> .
                @prefix acl: <http://www.w3.org/ns/auth/acl#> .
                <%1$s> acp:resource <%2$s> ; acp:accessControl <%1$s#ac> .
                <%1$s#ac> a acp:AccessControl ; acp:apply <%1$s#policy> .
                <%1$s#policy> a acp:Policy ; acp:allOf <%1$s#reader> ; acp:allow acl:Read .
                <%1$s#reader> a acp:Matcher ; acp:agent <%3$s> .
                """.formatted(acr, uri, agent);
        Model m = ModelFactory.createDefaultModel();
        RDFDataMgr.read(m, new ByteArrayInputStream(ttl.getBytes(StandardCharsets.UTF_8)), Lang.TURTLE);
        return m;
    }
}
