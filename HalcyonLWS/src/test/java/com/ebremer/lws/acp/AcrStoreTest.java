package com.ebremer.lws.acp;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.http.Problem;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.vocab.ACL;
import com.ebremer.lws.vocab.ACP;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins what a client may write into the shared ACP graph.
 *
 * <p>All ACP data for a storage lives in one graph, and {@code {resource}.acr} is a
 * client-writable window onto it. That makes {@link AcrStore#replace} the one place where a
 * caller's RDF becomes policy, and therefore the place where "Control over one resource" either
 * stays scoped to that resource or silently becomes Control over the storage.
 *
 * <p>Three defects this class exists to keep closed:
 * <ul>
 *   <li>F009 — the submitted ACR had to <em>declare</em> its target, but nothing stopped it
 *       declaring others as well. The engine finds a resource's policies by {@code acp:resource},
 *       so one extra object on that property pointed the submitter's own policies at somebody
 *       else's resource.</li>
 *   <li>F010 — the whole submitted model was spliced in with {@code acp.add(submitted)}, whatever
 *       its subjects were. A caller could therefore write triples about a <em>different</em> ACR
 *       node, editing policy it does not control, in the same request that edits its own.</li>
 *   <li>F059 — delete purged only {@code {resource}.acr}, so an access grant's ACR (a separate
 *       node carrying {@code acp:resource <target>}) outlived the resource. Under the slug naming
 *       policy a URI is reused, so re-creating the same name inherited a stranger's grant.</li>
 * </ul>
 */
class AcrStoreTest {

    private static final String TARGET = "https://host/W3Clws/case7/slide.tif";
    private static final String VICTIM = "https://host/W3Clws/case7/private.tif";
    private static final String ALICE = "https://alice.example/#me";

    private static final AgentContext ALICE_CTX = new AgentContext(ALICE, null, null, List.of());

    private Path dir;
    private LwsStore store;

    @BeforeEach
    void open() throws Exception {
        dir = Files.createTempDirectory("acr-store-test");
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

    /** A well-formed ACR for {@code target} granting Read to {@code ALICE}. */
    private static Model wellFormedAcr(String target) {
        Model m = ModelFactory.createDefaultModel();
        String acr = target + ".acr";
        Resource a = m.createResource(acr);
        Resource ac = m.createResource(acr + "#ac");
        Resource policy = m.createResource(acr + "#policy");
        Resource matcher = m.createResource(acr + "#matcher");
        a.addProperty(ACP.resource, m.createResource(target));
        a.addProperty(ACP.accessControl, ac);
        ac.addProperty(ACP.apply, policy);
        policy.addProperty(RDF.type, ACP.Policy);
        policy.addProperty(ACP.anyOf, matcher);
        policy.addProperty(ACP.allow, ACL.Read);
        matcher.addProperty(ACP.agent, m.createResource(ALICE));
        return m;
    }

    private Set<AccessMode> modes(String uri) {
        return store.read(() -> new AcpEngine(store).modes(ALICE_CTX, uri));
    }

    // ------------------------------------------------------------- baseline

    @Test
    void aWellFormedAcrRoundTripsAndGoverns() {
        store.write(() -> AcrStore.replace(store, TARGET, wellFormedAcr(TARGET)));
        assertEquals(Set.of(AccessMode.READ), modes(TARGET));
        assertTrue(store.read(() -> AcrStore.read(store, TARGET))
                .contains(r(TARGET + ".acr"), ACP.resource, r(TARGET)));
    }

    @Test
    void anAcrThatDoesNotClaimItsOwnResourceIsRefused() {
        Model m = wellFormedAcr(TARGET);
        m.removeAll(r(TARGET + ".acr"), ACP.resource, null);
        assertThrows(Problem.class, () -> store.write(() -> AcrStore.replace(store, TARGET, m)));
    }

    @Test
    void aPolicyWithNoMatchersIsRefusedRatherThanStoredAsANoOp() {
        Model m = wellFormedAcr(TARGET);
        m.removeAll(r(TARGET + ".acr#policy"), ACP.anyOf, null);
        assertThrows(Problem.class, () -> store.write(() -> AcrStore.replace(store, TARGET, m)));
    }

    // ------------------------------------------------------------------ F009

    /**
     * The submitted ACR claims its own resource — satisfying the existing check — and a second
     * one alongside it. Because the engine resolves a resource's ACRs by {@code acp:resource},
     * accepting this points the submitter's policy at a resource they were never given Control
     * over.
     */
    @Test
    void anAcrMayNotAlsoClaimSomebodyElsesResource() {
        Model m = wellFormedAcr(TARGET);
        m.add(r(TARGET + ".acr"), ACP.resource, r(VICTIM));

        assertThrows(Problem.class, () -> store.write(() -> AcrStore.replace(store, TARGET, m)),
                "an ACR that claims a second resource must be refused");
        assertEquals(Set.of(), modes(VICTIM),
                "and the other resource must be left ungoverned by it");
    }

    // ------------------------------------------------------------------ F010

    /**
     * The submitted graph carries a triple whose subject is a <em>different</em> ACR node. Splicing
     * the model in wholesale would let one PUT edit policy attached to another resource's ACR.
     */
    @Test
    void foreignSubjectsAreNotSplicedIntoTheSharedGraph() {
        Model m = wellFormedAcr(TARGET);
        Resource foreignAcr = m.createResource(VICTIM + ".acr");
        m.add(foreignAcr, ACP.accessControl, m.createResource(TARGET + ".acr#ac"));

        assertThrows(Problem.class, () -> store.write(() -> AcrStore.replace(store, TARGET, m)),
                "a triple about another ACR must not ride along");

        store.read(() -> assertFalse(store.acp().contains(foreignAcr, ACP.accessControl),
                "nothing about the foreign ACR may reach the shared graph"));
    }

    /** Detached junk that the ACR does not reach is equally not the caller's to write. */
    @Test
    void triplesUnreachableFromTheAcrAreRefused() {
        Model m = wellFormedAcr(TARGET);
        m.add(m.createResource("urn:lws:grantacr:forged-1"), ACP.resource, r(VICTIM));

        assertThrows(Problem.class, () -> store.write(() -> AcrStore.replace(store, TARGET, m)));
        assertEquals(Set.of(), modes(VICTIM));
    }

    // ------------------------------------------------------------------ F059

    /**
     * An access grant installs its policy under its own ACR node ({@code urn:lws:grantacr:...})
     * carrying {@code acp:resource <target>}. That separation is deliberate — it is what lets a
     * grant survive the owner rewriting their own ACR — but on delete it means the grant outlives
     * the resource. Under the slug naming policy the URI is reused, so the next resource created
     * with that name silently inherits the dead grant.
     */
    @Test
    void deletingAResourceAlsoPurgesGrantInstalledPolicies() {
        // A grant, installed the way AccessSharing installs one.
        store.write(() -> {
            Model acp = store.acp();
            Resource acr = acp.createResource("urn:lws:grantacr:g1-0");
            Resource ac = acp.createResource("urn:lws:grantacr:g1-0#ac");
            Resource policy = acp.createResource("urn:lws:grantacr:g1-0#policy");
            Resource matcher = acp.createResource("urn:lws:grantacr:g1-0#matcher");
            acr.addProperty(RDF.type, ACP.AccessControlResource);
            acr.addProperty(ACP.resource, acp.createResource(TARGET));
            acr.addProperty(ACP.accessControl, ac);
            ac.addProperty(ACP.apply, policy);
            policy.addProperty(RDF.type, ACP.Policy);
            policy.addProperty(ACP.anyOf, matcher);
            policy.addProperty(ACP.allow, ACL.Read);
            matcher.addProperty(ACP.agent, acp.createResource(ALICE));
        });
        assertEquals(Set.of(AccessMode.READ), modes(TARGET), "the grant governs while it lives");

        store.write(() -> AcrStore.purge(store, TARGET));

        assertEquals(Set.of(), modes(TARGET),
                "after the resource is deleted, a re-created slug of the same name must not "
                + "inherit the grant");
    }

    /** The ordinary case still works: a resource's own ACR is purged with it. */
    @Test
    void deletingAResourcePurgesItsOwnAcr() {
        store.write(() -> AcrStore.replace(store, TARGET, wellFormedAcr(TARGET)));
        assertEquals(Set.of(AccessMode.READ), modes(TARGET));

        store.write(() -> AcrStore.purge(store, TARGET));
        assertEquals(Set.of(), modes(TARGET));
    }

    /** A purge must not strip policy that still governs a surviving resource. */
    @Test
    void purgingOneResourceLeavesAnotherResourcesAcrIntact() {
        store.write(() -> {
            AcrStore.replace(store, TARGET, wellFormedAcr(TARGET));
            AcrStore.replace(store, VICTIM, wellFormedAcr(VICTIM));
        });
        store.write(() -> AcrStore.purge(store, TARGET));

        assertEquals(Set.of(), modes(TARGET));
        assertEquals(Set.of(AccessMode.READ), modes(VICTIM),
                "the surviving resource keeps its own policy");
    }
}
