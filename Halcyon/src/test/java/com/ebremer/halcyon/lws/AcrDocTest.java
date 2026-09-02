package com.ebremer.halcyon.lws;

import com.ebremer.lws.vocab.ACP;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link AcrDoc}: the editor's ACP round-trip must be faithful, and
 * anything it cannot faithfully rebuild must be flagged non-representable —
 * a lossy rewrite of an access control document is a security bug, not a
 * rendering glitch.
 */
class AcrDocTest {

    private static final String TARGET = "https://x/W3Clws/data/report";
    private static final String ACR = TARGET + ".acr";

    private static AcrDoc.Row row(AcrDoc.AgentType type, String webid,
            boolean read, boolean write, boolean control, boolean self, boolean members) {
        AcrDoc.Row r = new AcrDoc.Row();
        r.setAgentType(type);
        r.setWebid(webid);
        r.setRead(read);
        r.setWrite(write);
        r.setControl(control);
        r.setSelf(self);
        r.setMembers(members);
        return r;
    }

    @Test
    void buildSatisfiesTheStoragesPutValidation() {
        Model m = AcrDoc.build(ACR, TARGET, List.of(
                row(AcrDoc.AgentType.WEBID, "https://x/users/alice", true, true, false, true, true)));
        Resource acr = m.getResource(ACR);
        assertTrue(m.contains(acr, ACP.resource, m.getResource(TARGET)),
                "putAcr refuses an ACR that does not declare its resource");
        for (Resource p : m.listSubjectsWithProperty(RDF.type, ACP.Policy).toList()) {
            assertTrue(m.contains(p, ACP.anyOf) || m.contains(p, ACP.allOf),
                    "putAcr refuses a policy without matchers");
        }
    }

    @Test
    void roundTripIsFaithful() {
        Model m = AcrDoc.build(ACR, TARGET, List.of(
                row(AcrDoc.AgentType.WEBID, "https://x/users/alice", true, true, false, true, true),
                row(AcrDoc.AgentType.PUBLIC, "", true, false, false, true, false),
                row(AcrDoc.AgentType.AUTHENTICATED, "", true, false, true, false, true)));

        AcrDoc.Parsed parsed = AcrDoc.parse(m, ACR);
        assertTrue(parsed.representable(), "our own canonical output must parse cleanly");
        assertEquals(3, parsed.rows().size());

        AcrDoc.Row alice = parsed.rows().stream()
                .filter(r -> r.getAgentType() == AcrDoc.AgentType.WEBID).findFirst().orElseThrow();
        assertEquals("https://x/users/alice", alice.getWebid());
        assertTrue(alice.isRead() && alice.isWrite() && !alice.isControl());
        assertTrue(alice.isSelf() && alice.isMembers());

        AcrDoc.Row anyone = parsed.rows().stream()
                .filter(r -> r.getAgentType() == AcrDoc.AgentType.PUBLIC).findFirst().orElseThrow();
        assertTrue(anyone.isSelf() && !anyone.isMembers());

        AcrDoc.Row signedIn = parsed.rows().stream()
                .filter(r -> r.getAgentType() == AcrDoc.AgentType.AUTHENTICATED).findFirst().orElseThrow();
        assertTrue(!signedIn.isSelf() && signedIn.isMembers());
        assertTrue(signedIn.isControl());

        // Rebuilding what was parsed yields the same graph — nothing gained, nothing lost.
        Model again = AcrDoc.build(ACR, TARGET, parsed.rows());
        assertTrue(m.isIsomorphicWith(again), "parse→build must be lossless");
    }

    @Test
    void emptyDocumentIsRepresentableAndBlank() {
        AcrDoc.Parsed parsed = AcrDoc.parse(ModelFactory.createDefaultModel(), ACR);
        assertTrue(parsed.representable());
        assertEquals(0, parsed.rows().size());
    }

    @Test
    void denyAndForeignStatementsForceRawMode() {
        Model m = AcrDoc.build(ACR, TARGET, List.of(
                row(AcrDoc.AgentType.PUBLIC, "", true, false, false, true, false)));
        Resource policy = m.listSubjectsWithProperty(RDF.type, ACP.Policy).next();
        m.add(policy, ACP.deny, m.createResource("http://www.w3.org/ns/auth/acl#Write"));
        assertFalse(AcrDoc.parse(m, ACR).representable(),
                "acp:deny is a rule the rows cannot show; rebuilding would drop it");

        Model m2 = AcrDoc.build(ACR, TARGET, List.of(
                row(AcrDoc.AgentType.PUBLIC, "", true, false, false, true, false)));
        m2.add(m2.createResource(ACR), m2.createProperty("https://schema.org/expires"),
                m2.createLiteral("2027-01-01"));
        assertFalse(AcrDoc.parse(m2, ACR).representable(),
                "an unconsumed statement means the editor did not understand everything");
    }

    @Test
    void clientOrIssuerMatchersForceRawMode() {
        Model m = AcrDoc.build(ACR, TARGET, List.of(
                row(AcrDoc.AgentType.PUBLIC, "", true, false, false, true, false)));
        Resource matcher = m.listSubjectsWithProperty(RDF.type, ACP.Matcher).next();
        m.add(matcher, ACP.client, m.createResource("https://apps.example/viewer"));
        assertFalse(AcrDoc.parse(m, ACR).representable(),
                "a client-constrained matcher must not be flattened to agent-only");
    }

    @Test
    void emptyRowsBuildABareAcrThatClearsOwnRules() {
        Model m = AcrDoc.build(ACR, TARGET, List.of());
        assertTrue(m.contains(m.getResource(ACR), ACP.resource, m.getResource(TARGET)));
        assertFalse(m.contains(null, RDF.type, ACP.Policy), "no rows, no policies");
        AcrDoc.Parsed parsed = AcrDoc.parse(m, ACR);
        assertTrue(parsed.representable());
        assertEquals(0, parsed.rows().size());
    }
}
