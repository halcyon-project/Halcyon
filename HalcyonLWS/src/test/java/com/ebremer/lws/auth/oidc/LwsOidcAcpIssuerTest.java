package com.ebremer.lws.auth.oidc;

import com.ebremer.lws.acp.AccessMode;
import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.vocab.ACP;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The end of the chain: an LWS-OIDC identity actually drives access on a real storage via an
 * {@code acp:issuer} policy. This wires the <em>real</em> {@link AcpEngine} over a real (temp)
 * TDB2 {@link LwsStore} and evaluates the {@link AgentContext} an LWS credential yields — the same
 * shape {@code LwsOidcVerifier} produced in the live check ({@code webId=https://ebremer.com/id/erich},
 * {@code issuer=…/realms/Halcyon}). A policy that grants Read to <em>any</em> identity from that
 * issuer lets the LWS agent read, while the public agent and an agent from a different issuer cannot.
 *
 * <p>The store is opened reflectively on a throwaway TDB2 because {@code LwsStore} is a
 * config-driven singleton; everything else is production code.
 */
class LwsOidcAcpIssuerTest {

    private static final String TARGET = "https://storage.example/W3Clws/secret";
    private static final String ACL_READ = "http://www.w3.org/ns/auth/acl#Read";
    private static final String WEBID = "https://ebremer.com/id/erich";
    private static final String ISSUER = "https://ebremer.com/auth/realms/Halcyon";

    private Path dir;
    private LwsStore store;

    @BeforeEach
    void open() throws Exception {
        dir = Files.createTempDirectory("lws-oidc-acp");
        Constructor<LwsStore> ctor = LwsStore.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        store = ctor.newInstance(dir.resolve("tdb2").toString());

        // Install an ACR on TARGET: a policy granting Read to any agent whose issuer is ISSUER.
        store.write(() -> {
            Model acp = store.acp();
            Resource target = acp.createResource(TARGET);
            Resource acr = acp.createResource(TARGET + ".acr#ac");
            Resource ac = acp.createResource(TARGET + ".acr#control");
            Resource policy = acp.createResource(TARGET + ".acr#policy");
            Resource matcher = acp.createResource(TARGET + ".acr#matcher");
            acr.addProperty(ACP.resource, target).addProperty(ACP.accessControl, ac);
            ac.addProperty(ACP.apply, policy);
            policy.addProperty(ACP.anyOf, matcher).addProperty(ACP.allow, acp.createResource(ACL_READ));
            matcher.addProperty(ACP.issuer, acp.createResource(ISSUER));
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
            // TDB2 may hold Windows locks; leaving a temp dir is harmless
        }
    }

    private boolean canRead(AgentContext ctx) {
        return store.read(() -> new AcpEngine(store).modes(ctx, TARGET).contains(AccessMode.READ));
    }

    @Test
    void anLwsIdentityFromTheNamedIssuerIsGrantedRead() {
        AgentContext lws = new AgentContext(WEBID, "lws-app", ISSUER, List.of());
        assertTrue(canRead(lws),
                "acp:issuer must grant Read to the LWS identity whose (discovered) issuer it names");
    }

    @Test
    void thePublicAgentIsNotGrantedByAnIssuerPolicy() {
        assertFalse(canRead(AgentContext.PUBLIC), "no credential, no issuer -> no match");
    }

    @Test
    void anIdentityFromAnotherIssuerIsNotGranted() {
        AgentContext other = new AgentContext(WEBID, "lws-app", "https://someone-else.example", List.of());
        assertFalse(canRead(other), "same WebID but a different issuer must not match the policy");
    }
}
