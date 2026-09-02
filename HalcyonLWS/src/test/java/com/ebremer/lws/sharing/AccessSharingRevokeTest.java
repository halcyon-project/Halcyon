package com.ebremer.lws.sharing;

import com.ebremer.lws.acp.AccessMode;
import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.vocab.ACL;
import com.ebremer.lws.vocab.ACP;
import com.ebremer.lws.vocab.LWSX;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins what revoking an access grant is allowed to delete.
 *
 * <p>F061. Revocation used to remove the graph <em>reachable</em> from the grant's ACR node, and
 * that walk went straight through the matcher's {@code acp:agent} object — a URI the requester
 * supplies as the grant's assignee. So an authenticated agent with Control over one resource of
 * their own could name the storage root's ACR as assignee, then revoke their own grant, and take
 * the root's entire policy tree with it: authorization for the whole storage destroyed by a DELETE
 * of something the attacker owned.
 *
 * <p>Reachability was never the right rule. {@code installPolicy} mints a closed node set and its
 * javadoc already promises revocation "removes exactly this and nothing else"; the fix is to
 * enumerate those nodes instead of walking edges a client can point wherever it likes.
 *
 * <p>{@code cfg} and {@code notify} are null here on purpose: {@code remove} reaches only the
 * store, so passing real ones would add setup without adding coverage.
 */
class AccessSharingRevokeTest {

    private static final String GRANT_ID = "g1";
    private static final String GRANT_ACR = "urn:lws:grantacr:" + GRANT_ID + "-0";
    private static final String TARGET = "https://host/W3Clws/mine";

    /** The node the attack aims at: the storage root's ACR, published in every acl Link header. */
    private static final String ROOT_ACR = "https://host/W3Clws/.acr";
    private static final String ROOT = "https://host/W3Clws/";
    private static final String CHILD = "https://host/W3Clws/somebody-elses.tif";
    private static final String ALICE = "https://alice.example/#me";

    private Path dir;
    private LwsStore store;

    @BeforeEach
    void open() throws Exception {
        dir = Files.createTempDirectory("access-sharing-revoke");
        Constructor<LwsStore> ctor = LwsStore.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        store = ctor.newInstance(dir.resolve("tdb2").toString());

        store.write(() -> {
            Model acp = store.acp();

            // The storage root's ACR: a memberAccessControl policy granting Read to Alice on
            // everything beneath it. This is what must survive.
            Resource rootAcr = acp.createResource(ROOT_ACR);
            Resource rootAc = acp.createResource(ROOT_ACR + "#ac");
            Resource rootPolicy = acp.createResource(ROOT_ACR + "#policy");
            Resource rootMatcher = acp.createResource(ROOT_ACR + "#matcher");
            rootAcr.addProperty(RDF.type, ACP.AccessControlResource);
            rootAcr.addProperty(ACP.resource, acp.createResource(ROOT));
            rootAcr.addProperty(ACP.memberAccessControl, rootAc);
            rootAc.addProperty(ACP.apply, rootPolicy);
            rootPolicy.addProperty(RDF.type, ACP.Policy);
            rootPolicy.addProperty(ACP.anyOf, rootMatcher);
            rootPolicy.addProperty(ACP.allow, ACL.Read);
            rootMatcher.addProperty(ACP.agent, acp.createResource(ALICE));

            // The attacker's grant on their own resource, exactly as installPolicy writes one --
            // except the assignee is the storage root's ACR node rather than a WebID.
            Resource acr = acp.createResource(GRANT_ACR);
            Resource ac = acp.createResource(GRANT_ACR + "#ac");
            Resource policy = acp.createResource(GRANT_ACR + "#policy");
            Resource matcher = acp.createResource(GRANT_ACR + "#matcher");
            matcher.addProperty(RDF.type, ACP.Matcher);
            matcher.addProperty(ACP.agent, rootAcr);      // <-- the crafted assignee
            policy.addProperty(RDF.type, ACP.Policy);
            policy.addProperty(ACP.allOf, matcher);
            policy.addProperty(ACP.allow, ACL.Read);
            ac.addProperty(RDF.type, ACP.AccessControl);
            ac.addProperty(ACP.apply, policy);
            acr.addProperty(RDF.type, ACP.AccessControlResource);
            acr.addProperty(ACP.resource, acp.createResource(TARGET));
            acr.addProperty(ACP.accessControl, ac);

            // The grant record that revocation is driven from. It lives in the sharing graph, so
            // the terms are built free-standing rather than bound to the ACP model.
            Model sharing = store.raw().getNamedModel(LWSX.SHARING_GRAPH);
            sharing.add(ResourceFactory.createResource("urn:lws:accessgrant:" + GRANT_ID),
                    LWSX.grantedPolicy, ResourceFactory.createResource(GRANT_ACR));
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

    private Set<AccessMode> childModes() {
        AgentContext alice = new AgentContext(ALICE, null, null, List.of());
        return store.read(() -> new AcpEngine(store).modes(alice, CHILD));
    }

    @Test
    void revokingAGrantCannotDeleteTheStorageRootsPolicy() {
        store.write(() -> {
            store.system().createResource(CHILD)
                    .addProperty(LWSX.parent, store.system().createResource(ROOT));
        });
        assertEquals(Set.of(AccessMode.READ), childModes(),
                "precondition: the root policy grants Read beneath it");

        // remove() mutates the models directly and assumes an ambient write transaction, the
        // same contract AcrStore.replace/purge carry; the servlet supplies one.
        store.write(() -> new AccessSharing(store, null, null).remove(true, GRANT_ID));

        assertEquals(Set.of(AccessMode.READ), childModes(),
                "revoking a grant must not destroy the storage root's policy");
        store.read(() -> assertTrue(
                store.acp().contains(store.acp().getResource(ROOT_ACR), ACP.memberAccessControl),
                "the root ACR itself must survive"));
    }

    @Test
    void revokingAGrantStillRemovesItsOwnNodes() {
        // remove() mutates the models directly and assumes an ambient write transaction, the
        // same contract AcrStore.replace/purge carry; the servlet supplies one.
        store.write(() -> new AccessSharing(store, null, null).remove(true, GRANT_ID));

        store.read(() -> {
            Model acp = store.acp();
            for (String uri : List.of(GRANT_ACR, GRANT_ACR + "#ac", GRANT_ACR + "#policy",
                    GRANT_ACR + "#matcher")) {
                assertFalse(acp.listStatements(acp.getResource(uri), null, (org.apache.jena.rdf.model.RDFNode) null)
                        .hasNext(), uri + " must be gone");
            }
        });
    }
}
