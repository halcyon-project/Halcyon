package com.ebremer.lws.sharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.acp.AccessMode;
import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.lws.http.Problem;
import com.ebremer.lws.store.LwsResource;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.store.ResourceRegistry;
import com.ebremer.lws.store.ResourceType;
import com.ebremer.lws.vocab.ACL;
import com.ebremer.lws.vocab.ACP;
import com.ebremer.lws.vocab.LWS;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the target matcher of an access grant against lws10-core §Targets.
 *
 * <p>A target object carries a {@code type} — {@code lws:DataResource}, {@code lws:Container} or
 * {@code lws:StorageResource} — saying which Storage Resources its {@code value}s select. This
 * storage used to read the {@code value}s and ignore the {@code type} entirely, which is the shape
 * of an over-grant: a grant describing data resources, pointed at a container, would install a
 * policy on that container, and a container's policy is inherited by everything beneath it. So the
 * grant would reach far past what its author described, and the author would have no way to tell.
 *
 * <p>Refusing is the same fail-closed rule this module already applies to a constraint it cannot
 * enforce: a grant promises that what it says is what happens, so a grant that cannot be honoured
 * exactly is not installed at all.
 */
class AccessGrantTargetTest {

    private static final String SITE = "https://localhost:8888";
    private static final String ROOT = SITE + "/W3Clws/";
    private static final String CONTAINER = ROOT + "notes/";
    private static final String DATA = ROOT + "list.txt";
    private static final String ALICE = "https://alice.example/#me";
    private static final String BOB = "https://bob.example/#me";

    private Path dir;
    private LwsStore store;
    private LwsStorageConfig cfg;
    private AccessSharing sharing;

    @BeforeEach
    void open() throws Exception {
        dir = Files.createTempDirectory("access-grant-target");
        Constructor<LwsStore> ctor = LwsStore.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        store = ctor.newInstance(dir.resolve("tdb2").toString());
        cfg = new LwsStorageConfig("/W3Clws", dir.resolve("content"), NamingPolicyType.UUID, SITE);
        // notify is null: nothing here reaches delivery, and a real one would add setup without
        // adding coverage (the same reasoning as AccessSharingRevokeTest).
        sharing = new AccessSharing(store, cfg, null);

        store.write(() -> {
            ResourceRegistry reg = new ResourceRegistry(store, cfg);
            reg.seedRoot();
            reg.create(container(CONTAINER, ROOT), null);
            reg.create(data(DATA, ROOT), null);

            // Alice controls the whole storage, so authorization never stands between the test and
            // the matcher check.
            Model acp = store.acp();
            Resource acr = acp.createResource(ROOT + ".acr");
            Resource ac = acp.createResource(ROOT + ".acr#ac");
            Resource policy = acp.createResource(ROOT + ".acr#policy");
            Resource matcher = acp.createResource(ROOT + ".acr#matcher");
            acr.addProperty(RDF.type, ACP.AccessControlResource);
            acr.addProperty(ACP.resource, acp.createResource(ROOT));
            acr.addProperty(ACP.memberAccessControl, ac);
            acr.addProperty(ACP.accessControl, ac);
            ac.addProperty(ACP.apply, policy);
            policy.addProperty(RDF.type, ACP.Policy);
            policy.addProperty(ACP.anyOf, matcher);
            policy.addProperty(ACP.allow, ACL.Control);
            policy.addProperty(ACP.allow, ACL.Read);
            matcher.addProperty(ACP.agent, acp.createResource(ALICE));
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

    private static LwsResource container(String uri, String parent) {
        return new LwsResource(uri, ResourceType.CONTAINER, List.of(), null, 0, Instant.now(),
                "\"c0\"", null, null, parent, 0, ALICE, ALICE, null);
    }

    private static LwsResource data(String uri, String parent) {
        return new LwsResource(uri, ResourceType.DATA_RESOURCE, List.of(), "text/plain", 5,
                Instant.now(), "\"d0\"", "k", "txt", parent, 0, ALICE, ALICE, null);
    }

    private String grant(String matcher, String target) {
        String doc = "{\"@context\": [\"" + LWS.CONTEXT + "\"],"
                + "\"type\": [\"AccessGrant\"],"
                + "\"storage\": \"" + ROOT + "\","
                + "\"access\": [{\"type\": [\"AccessPolicy\"],"
                + "  \"action\": [\"read\"],"
                + "  \"assignee\": \"" + BOB + "\","
                + "  \"target\": {" + (matcher == null ? "" : "\"type\": \"" + matcher + "\",")
                + "    \"value\": [\"" + target + "\"]}}]}";
        JsonObject parsed;
        try (var r = Json.createReader(new StringReader(doc))) {
            parsed = r.readObject();
        }
        AgentContext alice = new AgentContext(ALICE, "https://app.example/c", null, List.of());
        return sharing.createGrant(alice, new AcpEngine(store), parsed);
    }

    /** Create a grant from a raw document, as alice. */
    private String createRaw(String doc) {
        JsonObject parsed;
        try (var r = Json.createReader(new StringReader(doc))) {
            parsed = r.readObject();
        }
        AgentContext alice = new AgentContext(ALICE, "https://app.example/c", null, List.of());
        return sharing.createGrant(alice, new AcpEngine(store), parsed);
    }

    /**
     * The access data model (Touchstone access-grant-incomplete-refused): a grant missing a
     * REQUIRED property, or carrying a malformed one, is refused with a 4xx before anything is
     * installed -- in particular a target that is not an object, which used to fail with a 500.
     * The complete grant they are each derived from still installs.
     */
    @Test
    void aGrantOutsideTheAccessDataModelIsRefused() {
        String policy = "\"type\": [\"AccessPolicy\"], \"action\": [\"read\"], \"assignee\": \"" + BOB
                + "\", \"target\": {\"type\": \"DataResource\", \"value\": [\"" + DATA + "\"]}";
        String ctx = "\"@context\": [\"" + LWS.CONTEXT + "\"]";
        String type = "\"type\": [\"AccessGrant\"]";
        String storage = "\"storage\": \"" + ROOT + "\"";
        java.util.Map<String, String> defects = new java.util.LinkedHashMap<>();
        defects.put("no storage", "{" + ctx + "," + type + ",\"access\": [{" + policy + "}]}");
        defects.put("another storage", "{" + ctx + "," + type + ",\"storage\": \"https://other.test/\","
                + "\"access\": [{" + policy + "}]}");
        defects.put("policy without type", "{" + ctx + "," + type + "," + storage + ",\"access\": [{"
                + policy.replace("\"type\": [\"AccessPolicy\"], ", "") + "}]}");
        defects.put("policy type without AccessPolicy", "{" + ctx + "," + type + "," + storage
                + ",\"access\": [{" + policy.replace("\"AccessPolicy\"", "\"urn:x:other\"") + "}]}");
        defects.put("target not an object", "{" + ctx + "," + type + "," + storage + ",\"access\": [{"
                + "\"type\": [\"AccessPolicy\"], \"action\": [\"read\"], \"assignee\": \"" + BOB
                + "\", \"target\": \"" + DATA + "\"}]}");
        defects.put("inbox not a URI", "{" + ctx + "," + type + "," + storage + ",\"inbox\": \"not a uri\","
                + "\"access\": [{" + policy + "}]}");
        defects.put("empty access", "{" + ctx + "," + type + "," + storage + ",\"access\": []}");
        defects.forEach((name, doc) -> {
            Problem p = assertThrows(Problem.class, () -> createRaw(doc), name);
            assertTrue(p.status() >= 400 && p.status() < 500, name + ": " + p.status());
        });
        assertFalse(bobMay(DATA), "none of them granted anything");

        // No inbox here: this class runs without a notifier (see open()).
        assertTrue(installed(createRaw("{" + ctx + "," + type + "," + storage
                + ",\"access\": [{" + policy + "}]}")));
        assertTrue(bobMay(DATA), "the complete grant does");
    }

    private Problem refused(String matcher, String target) {
        return assertThrows(Problem.class, () -> grant(matcher, target));
    }

    @Test
    void aMatcherNamingTheTargetsKindInstallsThePolicy() {
        assertTrue(installed(grant("Container", CONTAINER)));
        assertTrue(installed(grant("DataResource", DATA)));
        assertTrue(bobMay(DATA), "…and the policy it installed is live");
    }

    @Test
    void storageResourceMatchesEitherKind() {
        // lws:StorageResource is the common supertype of the other two.
        assertTrue(installed(grant(LWS.StorageResource.getURI(), CONTAINER)));
        assertTrue(installed(grant("StorageResource", DATA)));
    }

    @Test
    void aMatcherIsAcceptedAsATermOrAsItsFullIri() {
        assertTrue(installed(grant(LWS.Container.getURI(), CONTAINER)));
        assertTrue(installed(grant(LWS.DataResource.getURI(), DATA)));
    }

    @Test
    void aDataResourceMatcherPointedAtAContainerIsRefused() {
        // The over-grant this check exists for: a container's policy is inherited by everything
        // beneath it, so honouring the value while ignoring the type would grant far more than the
        // grant described.
        assertEquals(422, refused("DataResource", CONTAINER).status());
        assertTrue(!bobMay(DATA), "and nothing was installed");
    }

    @Test
    void aContainerMatcherPointedAtADataResourceIsRefused() {
        assertEquals(422, refused("Container", DATA).status());
    }

    @Test
    void anUnknownMatcherIsRefusedRatherThanIgnored() {
        assertEquals(422, refused("https://example.org/EverythingMatcher", DATA).status());
        assertEquals(422, refused("Image", DATA).status());
    }

    @Test
    void aTargetWithoutAMatcherIsMalformed() {
        // "The target property MUST be an object containing the following properties: type, value."
        assertEquals(400, refused(null, DATA).status());
    }

    @Test
    void aTargetOutsideThisStorageIsRefusedBeforeTheMatcherIsConsidered() {
        assertEquals(422, refused("DataResource", "https://elsewhere.example/thing").status());
    }

    /** A grant is identified by its own URI under the AccessGrantService endpoint. */
    private boolean installed(String grantUri) {
        return grantUri.startsWith(cfg.accessGrantsUri() + "/");
    }

    private boolean bobMay(String target) {
        AgentContext bob = new AgentContext(BOB, "https://app.example/c", null, List.of());
        return store.read(() -> new AcpEngine(store).allows(bob, target, AccessMode.READ));
    }
}
