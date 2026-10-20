package com.ebremer.lws.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.acp.AccessMode;
import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.capability.CapabilityDescriptor;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.lws.notify.Notifications;
import com.ebremer.lws.search.Cursor;
import com.ebremer.lws.search.LwsQuery;
import com.ebremer.lws.search.SearchService;
import com.ebremer.lws.store.LinksetStore;
import com.ebremer.lws.store.LwsResource;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.store.ResourceRegistry;
import com.ebremer.lws.store.ResourceType;
import com.ebremer.lws.store.ShardedContentStore;
import com.ebremer.lws.vocab.ACL;
import com.ebremer.lws.vocab.ACP;
import com.ebremer.lws.vocab.LWS;
import com.ebremer.lws.vocab.LWSX;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins a move on a storage whose identifiers do not encode containment, against lws10-core, Moving
 * Resources (w3c/lws-protocol#237): the resource keeps its URI and only its containment changes,
 * atomically; a patch that is not a move this storage can make is refused with a 409; a move
 * needs the authorization of a delete of the resource (recursively, for a container) and of a
 * create in the destination; it is announced as one {@code Move}; and the search index follows it.
 *
 * <p>Driven below the servlet, in a write transaction exactly as the linkset PATCH opens one, over
 * a real (temp) TDB2 store and the real ACP engine.
 */
class MoveHandlerTest {

    private static final String SITE = "https://localhost:8888";
    private static final String ROOT = SITE + "/W3Clws/";
    private static final String SRC = ROOT + "src";
    private static final String DST = ROOT + "dst";
    private static final String SUB = ROOT + "sub";
    private static final String DEEP = ROOT + "deep";
    private static final String FILE = ROOT + "file";
    private static final String INNER = ROOT + "inner";
    private static final String ALICE = "https://alice.example/#me";
    private static final String BOB = "https://bob.example/#me";
    private static final String PERSON = "https://schema.org/Person";

    private Path dir;
    private LwsStore store;
    private LwsStorageConfig cfg;
    private Notifications notify;
    private int acrs;

    @BeforeEach
    void open() throws Exception {
        dir = Files.createTempDirectory("move-handler");
        Constructor<LwsStore> ctor = LwsStore.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        store = ctor.newInstance(dir.resolve("tdb2").toString());
        cfg = new LwsStorageConfig("/W3Clws", dir.resolve("content"), NamingPolicyType.UUID, SITE);
        notify = new Notifications(store, cfg);

        // root ─┬─ src ─┬─ file
        //       │       └─ sub ─┬─ inner
        //       │               └─ deep
        //       └─ dst
        store.write(() -> {
            ResourceRegistry reg = new ResourceRegistry(store, cfg);
            reg.seedRoot();
            reg.create(container(SRC, ROOT, reg.nextSeq()), null);
            reg.create(container(DST, ROOT, reg.nextSeq()), null);
            reg.create(data(FILE, SRC, reg.nextSeq()), null);
            reg.create(container(SUB, SRC, reg.nextSeq()), null);
            reg.create(data(INNER, SUB, reg.nextSeq()), null);
            reg.create(container(DEEP, SUB, reg.nextSeq()), null);
            // Alice controls the storage.
            grant(ROOT, ALICE, true, AccessMode.READ, AccessMode.WRITE, AccessMode.APPEND,
                    AccessMode.CONTROL);
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

    // --- The capability -----------------------------------------------------

    @Test
    void describesAMoveResourceCapabilityThatPreservesIdentifiers() {
        CapabilityDescriptor d = handler(EnumSet.allOf(ResourceType.class)).descriptor();

        assertNotNull(d);
        assertEquals("MoveResource", d.capability().type(),
                "the term the draft's example uses, which the LWS context defines");
        JsonObject props = d.capability().properties();
        assertEquals(List.of("DataResource", "Container"), strings(props, "appliesTo"));
        assertTrue(props.getBoolean("preservesIdentifier"),
                "a key-minting store files bytes under a key unrelated to the URI");
    }

    @Test
    void describesOnlyTheKindsItMoves() {
        assertEquals(List.of("DataResource"), strings(handler(EnumSet.of(ResourceType.DATA_RESOURCE))
                .descriptor().capability().properties(), "appliesTo"));
    }

    @Test
    void describesNothingWhenMovingIsOff() {
        MoveHandler off = handler(Set.of());
        assertFalse(off.enabled());
        assertNull(off.descriptor(), "a server that does not allow it MUST NOT describe a capability");
    }

    // --- The move -----------------------------------------------------------

    @Test
    void aDataResourceMovesAndKeepsItsUri() {
        store.write(() -> LinksetStore.replace(store, FILE,
                Map.of("license", List.of("https://creativecommons.org/licenses/by/4.0/"))));
        String srcTag = etag(SRC);
        String dstTag = etag(DST);
        long beforeSeq = store.read(() -> registry().find(DEEP).orElseThrow().seq());

        MoveHandler.Moved moved = move(FILE, List.of(DST), all(ALICE));

        assertEquals(FILE, moved.uri());
        assertEquals(DST, moved.parent());
        assertFalse(moved.renamed());
        store.read(() -> {
            ResourceRegistry reg = registry();
            LwsResource f = reg.find(FILE).orElseThrow();
            assertEquals(DST, f.parent(), "up derives from the parent, so it follows");
            assertTrue(f.seq() > beforeSeq, "a fresh position, past every existing member");
            assertEquals(List.of(FILE), uris(reg.childRefs(DST)));
            assertEquals(List.of(SUB), uris(reg.childRefs(SRC)));
            assertEquals(ALICE, f.ownedBy(), "the owner stays bound to the resource");
            assertEquals(Map.of("license", List.of("https://creativecommons.org/licenses/by/4.0/")),
                    LinksetStore.read(store, FILE), "user-defined metadata is unaffected");
        });
        assertNotEquals(srcTag, etag(SRC), "the ETag of the container left MUST change");
        assertNotEquals(dstTag, etag(DST), "the ETag of the container joined MUST change");
    }

    @Test
    void aContainerMovesWithWhatItHolds() {
        MoveHandler.Moved moved = move(SUB, List.of(DST), all(ALICE));

        assertFalse(moved.renamed());
        store.read(() -> {
            ResourceRegistry reg = registry();
            assertEquals(DST, reg.find(SUB).orElseThrow().parent());
            assertEquals(List.of(INNER, DEEP), uris(reg.childRefs(SUB)),
                    "its descendants keep their position relative to one another");
            assertEquals(List.of(FILE), uris(reg.childRefs(SRC)));
        });
    }

    @Test
    void theContainerItIsInAlreadyIsNoMove() {
        String srcTag = etag(SRC);
        assertNull(move(FILE, List.of(SRC), all(ALICE)));
        assertEquals(srcTag, etag(SRC));
    }

    @Test
    void aRelativeReferenceIsResolvedAgainstTheLinkset() {
        MoveHandler.Moved moved = move(FILE, List.of("dst"), all(ALICE));
        assertEquals(DST, moved.parent());
    }

    // --- Refusals -----------------------------------------------------------

    @Test
    void aMoveToNoContainerOrTwoIsRefused() {
        assertConflict(() -> move(FILE, List.of(), all(ALICE)));
        assertConflict(() -> move(FILE, List.of(DST, SUB), all(ALICE)));
        assertStaysIn(FILE, SRC);
    }

    @Test
    void theStorageRootCannotMove() {
        assertConflict(() -> move(ROOT, List.of(DST), all(ALICE)));
    }

    @Test
    void aMoveToAnotherStorageIsRefused() {
        assertConflict(() -> move(FILE, List.of("https://elsewhere.example/c/"), all(ALICE)));
        assertConflict(() -> move(FILE, List.of(SITE + "/W3ClwsSlash/"), all(ALICE)));
        assertStaysIn(FILE, SRC);
    }

    @Test
    void theDestinationMustBeAnExistingContainer() {
        assertConflict(() -> move(SUB, List.of(ROOT + "nowhere"), all(ALICE)));
        assertConflict(() -> move(SUB, List.of(FILE), all(ALICE)));
        assertStaysIn(SUB, SRC);
    }

    @Test
    void aDestinationTheAgentMayNotKnowGetsTheAnswerOfOneThatDoesNotExist() {
        // Bob may do everything to the source side and nothing at all to dst.
        store.write(() -> {
            grant(SRC, BOB, true, AccessMode.READ, AccessMode.WRITE);
            grant(SRC, BOB, false, AccessMode.READ, AccessMode.APPEND);
        });

        Problem hidden = assertThrows(Problem.class,
                () -> move(FILE, List.of(DST), acp(BOB)));
        Problem absent = assertThrows(Problem.class,
                () -> move(FILE, List.of(ROOT + "nowhere"), acp(BOB)));
        assertEquals(409, hidden.status());
        assertEquals(absent.status(), hidden.status());
        assertEquals(absent.getMessage().replace(ROOT + "nowhere", "X"),
                hidden.getMessage().replace(DST, "X"), "no oracle in the wording either");
    }

    @Test
    void aContainerCannotMoveIntoItselfOrItsDescendants() {
        Problem self = assertThrows(Problem.class, () -> move(SUB, List.of(SUB), all(ALICE)));
        assertEquals(409, self.status());
        Problem below = assertThrows(Problem.class, () -> move(SRC, List.of(DEEP), all(ALICE)));
        assertEquals(409, below.status());
        assertTrue(below.getMessage().contains("Cannot move a container into one of its own descendants"),
                below.getMessage());
        assertTrue(below.getMessage().contains(DEEP + " is a descendant of " + SRC), below.getMessage());
        assertStaysIn(SRC, ROOT);
        assertStaysIn(DEEP, SUB);
    }

    @Test
    void aKindTheCapabilityLeavesOutIsRefused() {
        MoveHandler dataOnly = handler(EnumSet.of(ResourceType.DATA_RESOURCE));
        Problem p = assertThrows(Problem.class, () -> store.write(() -> dataOnly.move(
                registry().find(SUB).orElseThrow(), List.of(DST), SUB + ".meta", all(ALICE), null)));
        assertEquals(409, p.status());
        assertStaysIn(SUB, SRC);

        MoveHandler containersOnly = handler(EnumSet.of(ResourceType.CONTAINER));
        assertEquals(409, assertThrows(Problem.class, () -> store.write(() -> containersOnly.move(
                registry().find(FILE).orElseThrow(), List.of(DST), FILE + ".meta", all(ALICE),
                null))).status());
    }

    // --- Authorization ------------------------------------------------------

    @Test
    void aMoveNeedsCreateInTheDestination() {
        // Bob may read, modify and delete the resource (Write on it, Append on its container),
        // and read the destination -- but not create in it.
        store.write(() -> {
            grant(FILE, BOB, false, AccessMode.READ, AccessMode.WRITE);
            grant(SRC, BOB, false, AccessMode.APPEND);
            grant(DST, BOB, false, AccessMode.READ);
        });
        Problem p = assertThrows(Problem.class, () -> move(FILE, List.of(DST), acp(BOB)));
        assertEquals(403, p.status());
        assertTrue(p.getMessage().contains("destination"), p.getMessage());
        assertStaysIn(FILE, SRC);
    }

    @Test
    void aMoveNeedsDeleteOfTheResource() {
        // Bob may modify the resource and create in the destination, but not take it out of src.
        store.write(() -> {
            grant(FILE, BOB, false, AccessMode.READ, AccessMode.WRITE);
            grant(DST, BOB, false, AccessMode.READ, AccessMode.APPEND);
        });
        Problem p = assertThrows(Problem.class, () -> move(FILE, List.of(DST), acp(BOB)));
        assertEquals(403, p.status());
        assertStaysIn(FILE, SRC);
    }

    @Test
    void aContainerMoveNeedsTheWholeSubtreeAndIsNeverPartial() {
        // Bob holds everything a delete of sub would need, bar Write on the one member it holds
        // that denies him.
        store.write(() -> {
            grant(SUB, BOB, true, AccessMode.READ, AccessMode.WRITE);
            grant(SUB, BOB, false, AccessMode.READ, AccessMode.WRITE);
            grant(SRC, BOB, false, AccessMode.APPEND);
            grant(DST, BOB, false, AccessMode.READ, AccessMode.APPEND);
            deny(INNER, BOB, AccessMode.WRITE);
        });
        Problem p = assertThrows(Problem.class, () -> move(SUB, List.of(DST), acp(BOB)));
        assertEquals(403, p.status());
        assertFalse(p.getMessage().contains(INNER), "the member that refused is not named");
        assertStaysIn(SUB, SRC);
        assertStaysIn(INNER, SUB);

        // Without the deny, the same agent moves it.
        store.write(() -> store.acp().removeAll(null, ACP.deny, null));
        assertNotNull(move(SUB, List.of(DST), acp(BOB)));
        assertStaysIn(SUB, DST);
    }

    @Test
    void anAnonymousAgentIsChallenged() {
        store.write(() -> {
            grant(FILE, null, false, AccessMode.READ, AccessMode.WRITE);
            grant(DST, null, false, AccessMode.READ);
        });
        Problem p = assertThrows(Problem.class, () -> move(FILE, List.of(DST), acp(null)));
        assertEquals(401, p.status());
    }

    // --- What follows from a move -------------------------------------------

    @Test
    void aMoveIsAnnouncedAsOneMoveToEverySubscriberWhoMayStillReadIt() {
        store.write(() -> {
            subscribe("alice-on-src", ALICE, SRC);
            subscribe("alice-on-dst", ALICE, DST);
            subscribe("alice-elsewhere", ALICE, SUB);
            // Bob reads what src holds, and nothing in dst: he sees it leave, as a delete.
            grant(SRC, BOB, true, AccessMode.READ);
            grant(SRC, BOB, false, AccessMode.READ);
            subscribe("bob-on-src", BOB, SRC);
        });

        MoveHandler.Moved moved = move(FILE, List.of(DST), all(ALICE));

        Map<String, JsonObject> told = new java.util.HashMap<>();
        for (Notifications.Planned p : moved.plan()) {
            assertEquals(1, p.activities().size(), "a single activity, not a Delete and a Create");
            told.put(p.subId(), p.activities().get(0));
        }
        assertEquals(Set.of("alice-on-src", "alice-on-dst", "bob-on-src"), told.keySet());
        for (String sub : List.of("alice-on-src", "alice-on-dst")) {
            JsonObject a = told.get(sub);
            assertEquals("Move", a.getJsonArray("type").getString(0), sub);
            assertEquals(SRC, a.getString("origin"), "the container it was removed from");
            assertEquals(DST, a.getString("target"), "the container it was added to");
            assertEquals(FILE, a.getJsonObject("object").getString("id"),
                    "the resource as it is after the move");
        }
        JsonObject gone = told.get("bob-on-src");
        assertEquals("Delete", gone.getJsonArray("type").getString(0));
        assertEquals(SRC, gone.getString("origin"));
    }

    @Test
    void theSearchIndexFollowsTheMove() {
        store.write(() -> {
            registry().replaceDeclaredTypes(FILE, List.of(PERSON));
            grant(DST, BOB, true, AccessMode.READ);
        });
        LwsQuery people = new LwsQuery(Map.of(LwsQuery.TYPE, List.of(List.of(PERSON))));
        AgentContext bob = new AgentContext(BOB, null, null, List.of());

        assertEquals(0, search(bob, people).total(), "bob reads nothing in src");
        assertTrue(searchTypes(bob).isEmpty());

        move(FILE, List.of(DST), all(ALICE));

        SearchService.Page page = search(bob, people);
        assertEquals(1, page.total(), "the policy dst's members inherit now reaches it");
        assertEquals(FILE, page.items().get(0).id());
        assertTrue(searchTypes(bob).contains(PERSON));
    }

    // --- Helpers ------------------------------------------------------------

    private MoveHandler handler(Set<ResourceType> appliesTo) {
        return new MoveHandler(store, cfg, new ShardedContentStore(dir.resolve("content")), notify,
                appliesTo);
    }

    private MoveHandler.Moved move(String uri, List<String> up, MoveHandler.Authority authz) {
        MoveHandler h = handler(EnumSet.allOf(ResourceType.class));
        return store.write(() -> h.move(registry().find(uri).orElseThrow(), up,
                uri + LwsStorageConfig.LINKSET_SUFFIX, authz, null));
    }

    private ResourceRegistry registry() {
        return new ResourceRegistry(store, cfg);
    }

    /** An agent that may do anything: what is being tested is not authorization. */
    private static MoveHandler.Authority all(String webId) {
        return new MoveHandler.Authority(uri -> EnumSet.allOf(AccessMode.class),
                (mode, what) -> Problem.forbidden(what));
    }

    /** The agent as the ACP engine judges it, refused as the servlet refuses. */
    private MoveHandler.Authority acp(String webId) {
        AgentContext agent = new AgentContext(webId, null, null, List.of());
        AcpEngine engine = new AcpEngine(store);
        return new MoveHandler.Authority(uri -> engine.modes(agent, uri),
                (mode, what) -> agent.isAuthenticated() ? Problem.forbidden("may not " + what)
                        : Problem.unauthorized("authenticate to " + what));
    }

    private String etag(String uri) {
        return store.read(() -> registry().find(uri).orElseThrow().etag());
    }

    private void assertStaysIn(String uri, String parent) {
        assertEquals(parent, store.read(() -> registry().find(uri).orElseThrow().parent()));
    }

    private static void assertConflict(Runnable r) {
        Problem p = assertThrows(Problem.class, r::run);
        assertEquals(409, p.status(), p.getMessage());
    }

    private SearchService.Page search(AgentContext agent, LwsQuery q) {
        return new SearchService(store, cfg, agent, new AcpEngine(store))
                .search(q, new Cursor(cfg.typeSearchUri(), q.fingerprint(), ""));
    }

    private List<String> searchTypes(AgentContext agent) {
        return new SearchService(store, cfg, agent, new AcpEngine(store)).types();
    }

    private static List<String> uris(List<ResourceRegistry.ChildRef> refs) {
        return refs.stream().map(ResourceRegistry.ChildRef::uri).toList();
    }

    private static List<String> strings(JsonObject o, String key) {
        return o.getJsonArray(key).stream().map(v -> ((JsonString) v).getString()).toList();
    }

    /** A policy for {@code agent} (the public, when null), on the resource or on its members. */
    private void grant(String uri, String agent, boolean members, AccessMode... modes) {
        policy(uri, agent, members, ACP.allow, modes);
    }

    private void deny(String uri, String agent, AccessMode... modes) {
        policy(uri, agent, false, ACP.deny, modes);
    }

    private void policy(String uri, String agent, boolean members,
            org.apache.jena.rdf.model.Property effect, AccessMode... modes) {
        Model acp = store.acp();
        String base = "urn:test:acr:" + (acrs++);
        Resource acr = acp.createResource(base);
        Resource ac = acp.createResource(base + "#ac");
        Resource policy = acp.createResource(base + "#policy");
        Resource matcher = acp.createResource(base + "#matcher");
        acr.addProperty(RDF.type, ACP.AccessControlResource);
        acr.addProperty(ACP.resource, acp.createResource(uri));
        acr.addProperty(members ? ACP.memberAccessControl : ACP.accessControl, ac);
        ac.addProperty(ACP.apply, policy);
        policy.addProperty(RDF.type, ACP.Policy);
        policy.addProperty(ACP.allOf, matcher);
        for (AccessMode m : modes) {
            policy.addProperty(effect, m.iri());
        }
        matcher.addProperty(ACP.agent, agent == null ? ACP.PublicAgent : acp.createResource(agent));
    }

    private void subscribe(String id, String webId, String topic) {
        Model g = store.raw().getNamedModel(LWSX.SUBSCRIPTION_GRAPH);
        Resource s = g.createResource("urn:lws:subscription:" + id);
        s.addProperty(RDF.type, LWS.WebhookSubscription);
        s.addProperty(LWSX.storage, g.createResource(cfg.storageRootUri()));
        s.addProperty(LWS.subscription, "https://inbox.example/" + id);
        s.addProperty(LWSX.ownedBy, g.createResource(webId));
        s.addProperty(LWS.topic, g.createResource(topic));
    }

    private static LwsResource container(String uri, String parent, long seq) {
        return new LwsResource(uri, ResourceType.CONTAINER, List.of(), null, 0, Instant.now(),
                "\"c0\"", null, null, parent, seq, ALICE, ALICE, null);
    }

    private static LwsResource data(String uri, String parent, long seq) {
        return new LwsResource(uri, ResourceType.DATA_RESOURCE, List.of(), "text/plain", 5,
                Instant.now(), "\"d0\"", "k-" + seq, ".txt", parent, seq, ALICE, ALICE, null);
    }
}
