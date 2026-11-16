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
import com.ebremer.lws.vocab.LWSX;
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
 * Pins the access profile's constraints (lws10-core 11.3.5): "A server advertising support for
 * this profile MUST support the following leftOperand values: client, format, type, purpose,
 * dateTime", and "when multiple constraint objects are present, all of them MUST be satisfied".
 *
 * <p>format, type and further client constraints are evaluated per request, against the resource
 * as it is then. purpose is accepted and never satisfied, since no request can state one. These
 * used to be refused with 422 (Touchstone access-grant-left-operands-accepted,
 * access-grant-constraint-format, access-grant-constraint-type).
 */
class AccessGrantConstraintTest {

    private static final String SITE = "https://localhost:8888";
    private static final String ROOT = SITE + "/W3Clws/";
    private static final String CONTAINER = ROOT + "notes/";
    private static final String DATA = ROOT + "list.txt";
    private static final String CSV = ROOT + "table.csv";
    private static final String ALICE = "https://alice.example/#me";
    private static final String BOB = "https://bob.example/#me";
    private static final String BOBS_CLIENT = "https://app.example/c";

    private Path dir;
    private LwsStore store;
    private LwsStorageConfig cfg;
    private AccessSharing sharing;

    @BeforeEach
    void open() throws Exception {
        dir = Files.createTempDirectory("access-grant-constraint");
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
            reg.create(data(DATA, ROOT, "text/plain"), null);
            reg.create(data(CSV, ROOT, "text/csv"), null);

            // Alice controls the whole storage, so authorization never stands between the test and
            // the constraint check.
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

    private static LwsResource data(String uri, String parent, String mediaType) {
        return new LwsResource(uri, ResourceType.DATA_RESOURCE, List.of(), mediaType, 5,
                Instant.now(), "\"d0\"", "k", "txt", parent, 0, ALICE, ALICE, null);
    }

    /** A read grant to bob on {@code targets}, with the given constraint objects (JSON). */
    private String grant(String constraints, String... targets) {
        StringBuilder values = new StringBuilder();
        for (String t : targets) {
            values.append(values.length() == 0 ? "" : ",").append('"').append(t).append('"');
        }
        String doc = "{\"@context\": [\"" + LWS.CONTEXT + "\"],"
                + "\"type\": [\"AccessGrant\"],"
                + "\"storage\": \"" + ROOT + "\","
                + "\"access\": [{\"type\": [\"AccessPolicy\"],"
                + "  \"action\": [\"read\"],"
                + "  \"assignee\": \"" + BOB + "\","
                + "  \"target\": {\"type\": \"StorageResource\", \"value\": [" + values + "]},"
                + "  \"constraint\": [" + constraints + "]}]}";
        JsonObject parsed;
        try (var r = Json.createReader(new StringReader(doc))) {
            parsed = r.readObject();
        }
        AgentContext alice = new AgentContext(ALICE, BOBS_CLIENT, null, List.of());
        return sharing.createGrant(alice, new AcpEngine(store), parsed);
    }

    private static String c(String left, String op, String right) {
        return "{\"leftOperand\": \"" + left + "\", \"operator\": \"" + op + "\", \"rightOperand\": " + right + "}";
    }

    private boolean bobMay(String target) {
        return bobWithClientMay(BOBS_CLIENT, target);
    }

    private boolean bobWithClientMay(String client, String target) {
        AgentContext bob = new AgentContext(BOB, client, null, List.of());
        return store.read(() -> new AcpEngine(store).allows(bob, target, AccessMode.READ));
    }

    @Test
    void aFormatConstraintReachesOnlyTheListedMediaTypes() {
        grant(c("format", "isAnyOf", "[\"text/plain\", \"application/json\"]"), DATA, CSV);
        assertTrue(bobMay(DATA), "text/plain is listed");
        assertFalse(bobMay(CSV), "text/csv is not");
    }

    @Test
    void aFormatConstraintFollowsTheResourceAsItIsNow() {
        grant(c("format", "eq", "\"text/plain\""), DATA);
        assertTrue(bobMay(DATA));
        // A PUT that changes the media type takes the resource out of the grant at once.
        store.write(() -> new ResourceRegistry(store, cfg).replaceContent(data(DATA, ROOT, "text/csv")));
        assertFalse(bobMay(DATA));
    }

    @Test
    void aFormatConstraintComparesMediaTypesByEssence() {
        grant(c("format", "eq", "\"Text/Plain; charset=utf-8\""), DATA);
        assertTrue(bobMay(DATA));
    }

    @Test
    void aTypeConstraintReachesOnlyThatType() {
        grant(c("type", "eq", "\"" + LWS.DataResource.getURI() + "\""), CONTAINER, DATA);
        assertTrue(bobMay(DATA), "a data resource");
        assertFalse(bobMay(CONTAINER), "not a container");
    }

    @Test
    void aPurposeConstraintIsAcceptedButNeverSatisfied() {
        String uri = grant(c("purpose", "isAnyOf", "[\"urn:purpose:research\"]"), DATA);
        assertTrue(uri.startsWith(cfg.accessGrantsUri() + "/"), "the grant is created");
        assertFalse(bobMay(DATA), "and gives nothing: no request can state its purpose");
    }

    @Test
    void aClientIsAnyOfConstraintAdmitsOnlyThoseClients() {
        grant(c("client", "isAnyOf", "[\"" + BOBS_CLIENT + "\", \"https://other.example/app\"]"), DATA);
        assertTrue(bobMay(DATA));
        assertFalse(bobWithClientMay("https://third.example/app", DATA));
    }

    @Test
    void twoClientConstraintsMustBothHold() {
        // Formerly the second overwrote the first, which granted to a client the grant excluded.
        grant(c("client", "eq", "\"https://other.example/app\"") + ","
                + c("client", "eq", "\"" + BOBS_CLIENT + "\""), DATA);
        assertFalse(bobMay(DATA));
        assertFalse(bobWithClientMay("https://other.example/app", DATA));
    }

    @Test
    void theTightestDateTimeBoundWins() {
        // Formerly the later lteq replaced the earlier, reopening a window the grant had closed.
        grant(c("dateTime", "lteq", "\"2000-01-02T00:00:00Z\"") + ","
                + c("dateTime", "lteq", "\"2999-12-31T23:59:59Z\""), DATA);
        assertFalse(bobMay(DATA));
    }

    @Test
    void everyConstraintMustHold() {
        grant(c("format", "eq", "\"text/plain\"") + "," + c("type", "eq", "\"" + LWS.Container.getURI() + "\""), DATA);
        assertFalse(bobMay(DATA), "the format holds but the type does not");
    }

    @Test
    void anOperandOrOperatorOutsideTheProfileIsRefused() {
        assertEquals(422, assertThrows(Problem.class,
                () -> grant(c("spatial", "eq", "\"urn:x\""), DATA)).status());
        assertEquals(422, assertThrows(Problem.class,
                () -> grant(c("format", "neq", "\"text/plain\""), DATA)).status());
        assertEquals(400, assertThrows(Problem.class,
                () -> grant(c("type", "eq", "\"DataResource\""), DATA)).status(), "a type value is a URI");
    }

    @Test
    void revokingTheGrantRemovesItsConstraintNodes() {
        String uri = grant(c("format", "eq", "\"text/plain\""), DATA);
        assertTrue(store.read(() -> store.acp().listStatements(null, LWSX.constraint, (org.apache.jena.rdf.model.RDFNode) null).hasNext()));
        store.write(() -> sharing.remove(true, uri.substring(uri.lastIndexOf('/') + 1)));
        assertFalse(store.read(() -> store.acp().listStatements(null, LWSX.leftOperand, (org.apache.jena.rdf.model.RDFNode) null).hasNext()));
        assertFalse(bobMay(DATA));
    }
}
