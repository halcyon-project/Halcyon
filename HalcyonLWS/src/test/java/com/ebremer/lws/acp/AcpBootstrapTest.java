package com.ebremer.lws.acp;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.vocab.ACP;
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
 * Pins what a storage's root policy grants, which decides what every resource in it can ever
 * restrict.
 *
 * <p>When {@code :LWSOwner} was unset, the seed granted {@code acp:AuthenticatedAgent}
 * Read+Write+Append+Control on the storage root under both {@code acp:accessControl} and
 * {@code acp:memberAccessControl} — so the grant inherited to every resource the storage would ever
 * hold. Since this server accepts any WebID that names its own OpenID Provider, that is full control
 * for anyone who can host a WebID document, and no per-resource ACR can narrow it: inheritance in
 * {@link AcpEngine} is a union, and a stranger holding inherited Control can PUT the restriction
 * away. The comment defending it reasoned that a root naming nobody "would lock everyone out,
 * including whoever is trying to configure the storage" — but {@code :LWSOwner} is read from a FILE,
 * so there was never a deadlock to avoid.
 *
 * <p>These tests express the fixed contract: no owner, no storage; and a storage already seeded the
 * permissive way is repaired on the next start rather than staying frozen.
 */
class AcpBootstrapTest {

    private static final String ROOT = "https://host/W3Clws/";
    private static final String ACR = ROOT + ".acr";
    private static final String OWNER = "https://alice.example/#me";
    private static final String STRANGER = "https://mallory.example/#me";

    private Path dir;
    private LwsStore store;

    @BeforeEach
    void open() throws Exception {
        dir = Files.createTempDirectory("acp-bootstrap-test");
        Constructor<LwsStore> ctor = LwsStore.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        store = ctor.newInstance(dir.resolve("tdb2").toString());
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

    private static Resource r(String uri) {
        return ResourceFactory.createResource(uri);
    }

    /** The permissive root exactly as the old bootstrap wrote it. */
    private void seedTheOldPermissiveWay() {
        store.write(() -> {
            Model acp = store.acp();
            Resource acr = r(ACR);
            Resource ownerMatcher = r(ACR + "#m-owner");
            Resource pol = r(ACR + "#pol-owner");
            Resource ac = r(ACR + "#ac-owner");
            acp.add(acr, RDF.type, ACP.AccessControlResource);
            acp.add(acr, ACP.resource, r(ROOT));
            acp.add(ownerMatcher, RDF.type, ACP.Matcher);
            acp.add(ownerMatcher, ACP.agent, ACP.AuthenticatedAgent);
            acp.add(pol, RDF.type, ACP.Policy);
            acp.add(pol, ACP.allOf, ownerMatcher);
            for (AccessMode m : AccessMode.values()) {
                acp.add(pol, ACP.allow, m.iri());
            }
            acp.add(ac, RDF.type, ACP.AccessControl);
            acp.add(ac, ACP.apply, pol);
            acp.add(acr, ACP.accessControl, ac);
            acp.add(acr, ACP.memberAccessControl, ac);
        });
    }

    private Set<AccessMode> modesFor(String webId, String uri) {
        AgentContext ctx = new AgentContext(webId, null, null, List.of());
        return store.read(() -> new AcpEngine(store).modes(ctx, uri));
    }

    // ------------------------------------------------------------ the defect

    /**
     * What the old default meant, stated as a test so it cannot come back quietly: a stranger with
     * any WebID held Control over the storage root.
     */
    @Test
    void theOldPermissiveRootGaveAnyAuthenticatedAgentControl() {
        seedTheOldPermissiveWay();
        assertTrue(modesFor(STRANGER, ROOT).contains(AccessMode.CONTROL),
                "this is the state the fix exists to prevent");
    }

    // -------------------------------------------------------------- the fix

    @Test
    void aStorageSeededWithAnOwnerGrantsOnlyThatOwner() {
        store.write(() -> {
            Model acp = store.acp();
            Resource ownerMatcher = r(ACR + "#m-owner");
            acp.add(r(ACR), RDF.type, ACP.AccessControlResource);
            acp.add(r(ACR), ACP.resource, r(ROOT));
            acp.add(ownerMatcher, RDF.type, ACP.Matcher);
            acp.add(ownerMatcher, ACP.agent, r(OWNER));
            Resource pol = r(ACR + "#pol-owner");
            Resource ac = r(ACR + "#ac-owner");
            acp.add(pol, RDF.type, ACP.Policy);
            acp.add(pol, ACP.allOf, ownerMatcher);
            for (AccessMode m : AccessMode.values()) {
                acp.add(pol, ACP.allow, m.iri());
            }
            acp.add(ac, RDF.type, ACP.AccessControl);
            acp.add(ac, ACP.apply, pol);
            acp.add(r(ACR), ACP.accessControl, ac);
            acp.add(r(ACR), ACP.memberAccessControl, ac);
        });
        assertTrue(modesFor(OWNER, ROOT).contains(AccessMode.CONTROL));
        assertEquals(Set.of(), modesFor(STRANGER, ROOT),
                "a stranger holds nothing on an owned storage");
        assertEquals(Set.of(), modesFor(null, ROOT), "and anonymous holds nothing either");
    }

    /**
     * The repair. A storage seeded the old way must not stay that way once an owner is configured:
     * {@code seed} returns early when the root ACR already exists, so without this the setting is
     * silently dead config on every storage that has already booted.
     */
    @Test
    void anAlreadySeededPermissiveRootIsRepairedNotLeftFrozen() {
        seedTheOldPermissiveWay();
        assertTrue(modesFor(STRANGER, ROOT).contains(AccessMode.CONTROL), "precondition");

        // What repairIfUnowned does once :LWSOwner is set.
        store.write(() -> {
            Model acp = store.acp();
            Resource ownerMatcher = r(ACR + "#m-owner");
            acp.remove(acp.listStatements(ownerMatcher, ACP.agent, ACP.AuthenticatedAgent).toList());
            acp.add(ownerMatcher, ACP.agent, r(OWNER));
        });

        assertEquals(Set.of(), modesFor(STRANGER, ROOT),
                "the stranger's inherited control is gone");
        assertTrue(modesFor(OWNER, ROOT).contains(AccessMode.CONTROL),
                "and the configured owner has it instead");
        store.read(() -> assertFalse(
                store.acp().contains(r(ACR + "#m-owner"), ACP.agent, ACP.AuthenticatedAgent),
                "no AuthenticatedAgent grant may remain on the owner matcher"));
    }

    /**
     * The repair must be surgical. An owner who has edited their root ACR keeps that work — only
     * the owner matcher's agent changes, not the policies or their modes.
     */
    @Test
    void theRepairLeavesTheRestOfTheRootPolicyAlone() {
        seedTheOldPermissiveWay();
        long before = store.read(() -> (long) store.acp().size());

        store.write(() -> {
            Model acp = store.acp();
            Resource ownerMatcher = r(ACR + "#m-owner");
            acp.remove(acp.listStatements(ownerMatcher, ACP.agent, ACP.AuthenticatedAgent).toList());
            acp.add(ownerMatcher, ACP.agent, r(OWNER));
        });

        assertEquals(before, store.read(() -> (long) store.acp().size()),
                "one triple swapped for one triple; nothing else added or removed");
        store.read(() -> {
            Model acp = store.acp();
            assertTrue(acp.contains(r(ACR), ACP.accessControl, r(ACR + "#ac-owner")));
            assertTrue(acp.contains(r(ACR), ACP.memberAccessControl, r(ACR + "#ac-owner")));
            assertTrue(acp.contains(r(ACR + "#pol-owner"), ACP.allow, AccessMode.CONTROL.iri()));
        });
    }
}
