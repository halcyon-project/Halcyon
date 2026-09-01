package com.ebremer.lws.acp;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.vocab.ACL;
import com.ebremer.lws.vocab.ACP;
import com.ebremer.lws.vocab.LWSX;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.jena.rdf.model.LiteralRequiredException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link AcpEngine}, the decision engine every LWS authorization passes through.
 *
 * <p>F096: before this class the engine was covered by a single policy shape — one
 * {@code anyOf} matcher granting Read — inherited from {@code LwsOidcAcpIssuerTest}, whose
 * subject is the OIDC issuer chain rather than the engine. Deny, the {@code allOf}/{@code noneOf}
 * branches, the inheritance asymmetry, the fail-closed guards and the time-boxing were all
 * untested, so any of them could have been deleted or inverted without turning the build red.
 * That is the wrong safety net to have while refactoring an authorization engine.
 *
 * <p>Everything here drives the real engine over a real (temp) TDB2 {@link LwsStore}, opened
 * reflectively because the store is a config-driven singleton — the same approach
 * {@code LwsOidcAcpIssuerTest} already uses. No part of the decision path is mocked.
 *
 * <p>Two properties are worth naming because a plausible-looking refactor destroys them:
 * <strong>a policy with no matchers must never be satisfied</strong> (read literally it would be
 * vacuously true and grant the world), and <strong>an ancestor's own {@code acp:accessControl}
 * must never reach a descendant</strong> (only {@code acp:memberAccessControl} inherits).
 */
class AcpEngineTest {

    private static final String ROOT = "https://host/W3Clws/";
    private static final String PARENT = "https://host/W3Clws/case7/";
    private static final String TARGET = "https://host/W3Clws/case7/slide.tif";

    private static final String ALICE = "https://alice.example/#me";
    private static final String BOB = "https://bob.example/#me";
    private static final String ISSUER = "https://issuer.example/realms/lws";
    private static final String CLIENT = "https://app.example/id";
    private static final String VC_TYPE = "https://example.org/vc/StaffCredential";

    private static final AgentContext ANON = AgentContext.PUBLIC;
    private static final AgentContext ALICE_CTX =
            new AgentContext(ALICE, CLIENT, ISSUER, List.of(VC_TYPE));
    private static final AgentContext BOB_CTX = new AgentContext(BOB, null, null, List.of());

    private Path dir;
    private LwsStore store;

    @BeforeEach
    void open() throws Exception {
        dir = Files.createTempDirectory("acp-engine-test");
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
            // TDB2 may still hold locks; a leftover temp dir is harmless
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Populate the ACP graph. */
    private void acp(Consumer<Model> body) {
        store.write(() -> body.accept(store.acp()));
    }

    /** Populate the internal system graph (containment, creator, owner). */
    private void sys(Consumer<Model> body) {
        store.write(() -> body.accept(store.system()));
    }

    /** A fresh engine every time, as every write path in the module does. */
    private Set<AccessMode> modesOf(AgentContext ctx, String uri) {
        return store.read(() -> new AcpEngine(store).modes(ctx, uri));
    }

    private Set<AccessMode> modes(AgentContext ctx) {
        return modesOf(ctx, TARGET);
    }

    /**
     * Install an ACR on {@code target} linked by {@code link} — {@code acp:accessControl} to
     * govern the resource itself, {@code acp:memberAccessControl} to govern its descendants —
     * and return the {@code acp:AccessControl} node that policies hang off.
     */
    private static Resource control(Model acp, String target, Property link, String id) {
        Resource acr = acp.createResource(target + ".acr#" + id);
        Resource ac = acp.createResource(target + ".acr#" + id + "-ac");
        acr.addProperty(ACP.resource, acp.createResource(target));
        acr.addProperty(link, ac);
        return ac;
    }

    /** The ACR node itself, so a test can time-box it. */
    private static Resource acrNode(Model acp, String target, String id) {
        return acp.createResource(target + ".acr#" + id);
    }

    private static Resource policy(Model acp, Resource ac, String id) {
        Resource p = acp.createResource("urn:test:policy:" + id);
        ac.addProperty(ACP.apply, p);
        return p;
    }

    private static Resource matcher(Model acp, String id) {
        return acp.createResource("urn:test:matcher:" + id);
    }

    /** The common shape: an ACR on TARGET whose single policy allows {@code modes} to {@code agent}. */
    private void grantOnTarget(Resource agent, Resource... allow) {
        acp(m -> {
            Resource ac = control(m, TARGET, ACP.accessControl, "own");
            Resource p = policy(m, ac, "grant");
            Resource mt = matcher(m, "agent");
            mt.addProperty(ACP.agent, agent);
            p.addProperty(ACP.anyOf, mt);
            for (Resource mode : allow) {
                p.addProperty(ACP.allow, mode);
            }
        });
    }

    // ------------------------------------------------------------ fail-closed

    @Nested
    class FailsClosed {

        @Test
        void noAcrAtAllGrantsNothing() {
            assertEquals(Set.of(), modes(ALICE_CTX));
            assertEquals(Set.of(), modes(ANON));
        }

        /**
         * The guard the class javadoc calls load-bearing. A policy with neither {@code allOf} nor
         * {@code anyOf} is vacuously satisfiable when read literally, so it would grant its modes
         * to everyone including the anonymous agent. Deleting the two-line guard in
         * {@code satisfied()} turns every truncated ACR into world-readable.
         */
        @Test
        void aPolicyWithNoMatchersGrantsNobodyNotEveryone() {
            acp(m -> {
                Resource ac = control(m, TARGET, ACP.accessControl, "own");
                policy(m, ac, "naked").addProperty(ACP.allow, ACL.Read);
            });
            assertEquals(Set.of(), modes(ANON), "an unmatched policy must not grant the world");
            assertEquals(Set.of(), modes(ALICE_CTX));
        }

        /** Same rule one level down: a matcher constraining no attribute matches nobody. */
        @Test
        void aMatcherThatConstrainsNothingMatchesNobody() {
            acp(m -> {
                Resource ac = control(m, TARGET, ACP.accessControl, "own");
                Resource p = policy(m, ac, "grant");
                p.addProperty(ACP.anyOf, matcher(m, "empty"));
                p.addProperty(ACP.allow, ACL.Read);
            });
            assertEquals(Set.of(), modes(ALICE_CTX));
            assertEquals(Set.of(), modes(ANON));
        }

        /** An {@code acp:apply} pointing at a literal, or an allow naming an unknown mode. */
        @Test
        void malformedPolicyAndModeReferencesAreIgnoredNotTrusted() {
            acp(m -> {
                Resource ac = control(m, TARGET, ACP.accessControl, "own");
                ac.addProperty(ACP.apply, m.createLiteral("not a policy"));
                Resource p = policy(m, ac, "grant");
                Resource mt = matcher(m, "agent");
                mt.addProperty(ACP.agent, ACP.PublicAgent);
                p.addProperty(ACP.anyOf, mt);
                p.addProperty(ACP.allow, m.createResource("https://example.org/ns#Teleport"));
                p.addProperty(ACP.allow, m.createLiteral("Read"));
                p.addProperty(ACP.allow, ACL.Read);
            });
            assertEquals(Set.of(AccessMode.READ), modes(ANON),
                    "unknown and non-URI modes are dropped, the real one survives");
        }
    }

    // --------------------------------------------------------- matcher logic

    @Nested
    class MatcherCombination {

        private void policyWith(Property combinator, boolean... matching) {
            acp(m -> {
                Resource ac = control(m, TARGET, ACP.accessControl, "own");
                Resource p = policy(m, ac, "grant");
                p.addProperty(ACP.allow, ACL.Read);
                for (int i = 0; i < matching.length; i++) {
                    Resource mt = matcher(m, combinator.getLocalName() + i);
                    // A matcher matches Alice iff we point it at her WebID; otherwise at Bob's.
                    mt.addProperty(ACP.agent, m.createResource(matching[i] ? ALICE : BOB));
                    p.addProperty(combinator, mt);
                }
                if (combinator == ACP.noneOf) {
                    // noneOf alone would leave the policy unsatisfiable by the fail-closed rule.
                    Resource anchor = matcher(m, "anchor");
                    anchor.addProperty(ACP.agent, ACP.AuthenticatedAgent);
                    p.addProperty(ACP.anyOf, anchor);
                }
            });
        }

        @Test
        void allOfRequiresEveryMatcher() {
            policyWith(ACP.allOf, true, true);
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX));
        }

        @Test
        void allOfFailsWhenOneMatcherDoesNot() {
            policyWith(ACP.allOf, true, false);
            assertEquals(Set.of(), modes(ALICE_CTX), "every allOf matcher must match");
        }

        @Test
        void anyOfNeedsOnlyOneMatcher() {
            policyWith(ACP.anyOf, false, true);
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX));
        }

        @Test
        void anyOfFailsWhenNoMatcherMatches() {
            policyWith(ACP.anyOf, false, false);
            assertEquals(Set.of(), modes(ALICE_CTX));
        }

        @Test
        void noneOfVetoesAnOtherwiseSatisfiedPolicy() {
            policyWith(ACP.noneOf, true);
            assertEquals(Set.of(), modes(ALICE_CTX), "a matching noneOf must veto");
        }

        @Test
        void noneOfThatDoesNotMatchLeavesThePolicyIntact() {
            policyWith(ACP.noneOf, false);
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX));
        }

        /** allOf and anyOf are ANDed with each other, not alternatives. */
        @Test
        void allOfAndAnyOfMustBothBeSatisfied() {
            acp(m -> {
                Resource ac = control(m, TARGET, ACP.accessControl, "own");
                Resource p = policy(m, ac, "grant");
                p.addProperty(ACP.allow, ACL.Read);
                Resource must = matcher(m, "must");
                must.addProperty(ACP.agent, ACP.AuthenticatedAgent);
                Resource any = matcher(m, "any");
                any.addProperty(ACP.agent, m.createResource(BOB));
                p.addProperty(ACP.allOf, must);
                p.addProperty(ACP.anyOf, any);
            });
            assertEquals(Set.of(), modes(ALICE_CTX),
                    "allOf satisfied but anyOf not: the policy must not apply");
            assertEquals(Set.of(AccessMode.READ), modes(BOB_CTX));
        }

        /** Within one matcher, attributes are ANDed: agent AND issuer must both hold. */
        @Test
        void attributesWithinAMatcherAreAnded() {
            acp(m -> {
                Resource ac = control(m, TARGET, ACP.accessControl, "own");
                Resource p = policy(m, ac, "grant");
                p.addProperty(ACP.allow, ACL.Read);
                Resource mt = matcher(m, "both");
                mt.addProperty(ACP.agent, m.createResource(ALICE));
                mt.addProperty(ACP.issuer, m.createResource("https://other.example/realm"));
                p.addProperty(ACP.anyOf, mt);
            });
            assertEquals(Set.of(), modes(ALICE_CTX),
                    "right agent, wrong issuer: the matcher must not match");
        }
    }

    // -------------------------------------------------------------- deny wins

    @Nested
    class DenyBeatsAllow {

        @Test
        void denyInTheSamePolicyRemovesTheMode() {
            acp(m -> {
                Resource ac = control(m, TARGET, ACP.accessControl, "own");
                Resource p = policy(m, ac, "grant");
                Resource mt = matcher(m, "auth");
                mt.addProperty(ACP.agent, ACP.AuthenticatedAgent);
                p.addProperty(ACP.anyOf, mt);
                p.addProperty(ACP.allow, ACL.Read);
                p.addProperty(ACP.deny, ACL.Read);
            });
            assertEquals(Set.of(), modes(ALICE_CTX));
        }

        /** The case that matters: allow and deny arrive from two different satisfied policies. */
        @Test
        void denyFromAnotherPolicyStillWins() {
            acp(m -> {
                Resource ac = control(m, TARGET, ACP.accessControl, "own");

                Resource allow = policy(m, ac, "allow");
                Resource everyone = matcher(m, "auth");
                everyone.addProperty(ACP.agent, ACP.AuthenticatedAgent);
                allow.addProperty(ACP.anyOf, everyone);
                allow.addProperty(ACP.allow, ACL.Read);
                allow.addProperty(ACP.allow, ACL.Write);

                Resource deny = policy(m, ac, "deny");
                Resource justBob = matcher(m, "bob");
                justBob.addProperty(ACP.agent, m.createResource(BOB));
                deny.addProperty(ACP.anyOf, justBob);
                deny.addProperty(ACP.deny, ACL.Write);
            });
            assertEquals(Set.of(AccessMode.READ, AccessMode.WRITE, AccessMode.APPEND),
                    modes(ALICE_CTX), "Alice is not caught by the deny policy");
            assertEquals(Set.of(AccessMode.READ), modes(BOB_CTX),
                    "Bob's Write is denied, and Append does not survive it either");
        }

        /**
         * Write implies Append, through the engine rather than {@link AccessMode} directly.
         * The inbox pattern — Append without Read or Write — must stay expressible.
         */
        @Test
        void writeImpliesAppendButDenyingWriteDoesNotDenyAppend() {
            acp(m -> {
                Resource ac = control(m, TARGET, ACP.accessControl, "own");
                Resource p = policy(m, ac, "inbox");
                Resource mt = matcher(m, "auth");
                mt.addProperty(ACP.agent, ACP.AuthenticatedAgent);
                p.addProperty(ACP.anyOf, mt);
                p.addProperty(ACP.allow, ACL.Append);
                p.addProperty(ACP.deny, ACL.Write);
            });
            assertEquals(Set.of(AccessMode.APPEND), modes(ALICE_CTX),
                    "append-only inbox must survive a Write denial");
        }

        @Test
        void anExplicitAppendDenialIsNotResurrectedByTheWriteImplication() {
            acp(m -> {
                Resource ac = control(m, TARGET, ACP.accessControl, "own");
                Resource p = policy(m, ac, "odd");
                Resource mt = matcher(m, "auth");
                mt.addProperty(ACP.agent, ACP.AuthenticatedAgent);
                p.addProperty(ACP.anyOf, mt);
                p.addProperty(ACP.allow, ACL.Write);
                p.addProperty(ACP.deny, ACL.Append);
            });
            assertEquals(Set.of(AccessMode.WRITE), modes(ALICE_CTX));
        }
    }

    // --------------------------------------------------------- agent matching

    @Nested
    class AgentMatching {

        @Test
        void publicAgentMatchesTheAnonymousRequest() {
            grantOnTarget(ACP.PublicAgent, ACL.Read);
            assertEquals(Set.of(AccessMode.READ), modes(ANON));
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX),
                    "public also covers an authenticated agent");
        }

        @Test
        void authenticatedAgentExcludesTheAnonymousRequest() {
            grantOnTarget(ACP.AuthenticatedAgent, ACL.Read);
            assertEquals(Set.of(), modes(ANON));
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX));
        }

        @Test
        void aConcreteWebIdMatchesOnlyThatAgent() {
            acp(m -> {
                Resource ac = control(m, TARGET, ACP.accessControl, "own");
                Resource p = policy(m, ac, "grant");
                Resource mt = matcher(m, "alice");
                mt.addProperty(ACP.agent, m.createResource(ALICE));
                p.addProperty(ACP.anyOf, mt);
                p.addProperty(ACP.allow, ACL.Read);
            });
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX));
            assertEquals(Set.of(), modes(BOB_CTX));
            assertEquals(Set.of(), modes(ANON));
        }

        @Test
        void creatorAgentMatchesOnlyTheRecordedCreator() {
            sys(m -> m.createResource(TARGET)
                    .addProperty(LWSX.createdBy, m.createResource(ALICE)));
            grantOnTarget(ACP.CreatorAgent, ACL.Read);
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX));
            assertEquals(Set.of(), modes(BOB_CTX));
            assertEquals(Set.of(), modes(ANON), "anonymous can never be the creator");
        }

        @Test
        void ownerAgentMatchesOnlyTheRecordedOwner() {
            sys(m -> m.createResource(TARGET)
                    .addProperty(LWSX.ownedBy, m.createResource(BOB)));
            grantOnTarget(ACP.OwnerAgent, ACL.Control);
            assertEquals(Set.of(AccessMode.CONTROL), modes(BOB_CTX));
            assertEquals(Set.of(), modes(ALICE_CTX));
        }

        /** With no creator recorded at all, CreatorAgent must match nobody rather than everybody. */
        @Test
        void creatorAgentMatchesNobodyWhenNoCreatorIsRecorded() {
            grantOnTarget(ACP.CreatorAgent, ACL.Read);
            assertEquals(Set.of(), modes(ALICE_CTX));
            assertEquals(Set.of(), modes(BOB_CTX));
        }
    }

    // ------------------------------------------------- client / issuer / vc

    @Nested
    class ContextAttributes {

        private void matcherOn(Property attr, String value) {
            acp(m -> {
                Resource ac = control(m, TARGET, ACP.accessControl, "own");
                Resource p = policy(m, ac, "grant");
                Resource mt = matcher(m, "ctx");
                mt.addProperty(attr, m.createResource(value));
                p.addProperty(ACP.anyOf, mt);
                p.addProperty(ACP.allow, ACL.Read);
            });
        }

        @Test
        void clientMatcherRequiresThatClient() {
            matcherOn(ACP.client, CLIENT);
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX));
            assertEquals(Set.of(), modes(BOB_CTX), "Bob presents no client id");
            assertEquals(Set.of(), modes(ANON));
        }

        @Test
        void issuerMatcherRequiresThatIssuer() {
            matcherOn(ACP.issuer, ISSUER);
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX));
            assertEquals(Set.of(), modes(BOB_CTX));
        }

        @Test
        void issuerMatcherRejectsADifferentIssuer() {
            matcherOn(ACP.issuer, "https://evil.example/realms/lws");
            assertEquals(Set.of(), modes(ALICE_CTX));
        }

        @Test
        void vcMatcherRequiresAHeldCredentialType() {
            matcherOn(ACP.vc, VC_TYPE);
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX));
            assertEquals(Set.of(), modes(BOB_CTX), "Bob holds no credentials");
        }
    }

    // ------------------------------------------------------------ inheritance

    @Nested
    class Inheritance {

        private void parentChain() {
            sys(m -> {
                m.createResource(TARGET).addProperty(LWSX.parent, m.createResource(PARENT));
                m.createResource(PARENT).addProperty(LWSX.parent, m.createResource(ROOT));
            });
        }

        @Test
        void memberAccessControlOnTheParentReachesTheChild() {
            parentChain();
            acp(m -> {
                Resource ac = control(m, PARENT, ACP.memberAccessControl, "members");
                Resource p = policy(m, ac, "inherited");
                Resource mt = matcher(m, "auth");
                mt.addProperty(ACP.agent, ACP.AuthenticatedAgent);
                p.addProperty(ACP.anyOf, mt);
                p.addProperty(ACP.allow, ACL.Read);
            });
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX));
        }

        /**
         * The asymmetry the class javadoc calls load-bearing. An ancestor's own
         * {@code acp:accessControl} governs the ancestor and must never reach a descendant —
         * collapsing the two link types would silently hand every container's own permissions
         * to everything beneath it.
         */
        @Test
        void anAncestorsOwnAccessControlMustNotReachTheChild() {
            parentChain();
            acp(m -> {
                Resource ac = control(m, PARENT, ACP.accessControl, "ownOfParent");
                Resource p = policy(m, ac, "parentOnly");
                Resource mt = matcher(m, "auth");
                mt.addProperty(ACP.agent, ACP.AuthenticatedAgent);
                p.addProperty(ACP.anyOf, mt);
                p.addProperty(ACP.allow, ACL.Read);
            });
            assertEquals(Set.of(), modes(ALICE_CTX),
                    "acp:accessControl governs the parent alone");
            assertEquals(Set.of(AccessMode.READ), modesOf(ALICE_CTX, PARENT),
                    "...and it does govern the parent");
        }

        /** The mirror of the above: a resource's own memberAccessControl does not govern itself. */
        @Test
        void aResourcesOwnMemberAccessControlDoesNotGovernItself() {
            acp(m -> {
                Resource ac = control(m, TARGET, ACP.memberAccessControl, "members");
                Resource p = policy(m, ac, "forMembers");
                Resource mt = matcher(m, "auth");
                mt.addProperty(ACP.agent, ACP.AuthenticatedAgent);
                p.addProperty(ACP.anyOf, mt);
                p.addProperty(ACP.allow, ACL.Read);
            });
            assertEquals(Set.of(), modes(ALICE_CTX));
        }

        @Test
        void inheritanceIsTransitiveThroughGrandparents() {
            parentChain();
            acp(m -> {
                Resource ac = control(m, ROOT, ACP.memberAccessControl, "members");
                Resource p = policy(m, ac, "fromRoot");
                Resource mt = matcher(m, "auth");
                mt.addProperty(ACP.agent, ACP.AuthenticatedAgent);
                p.addProperty(ACP.anyOf, mt);
                p.addProperty(ACP.allow, ACL.Read);
            });
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX),
                    "a grandparent's member policy inherits transitively");
        }

        @Test
        void anInheritedDenyOverridesALocalAllow() {
            parentChain();
            acp(m -> {
                Resource own = control(m, TARGET, ACP.accessControl, "own");
                Resource allow = policy(m, own, "localAllow");
                Resource anyone = matcher(m, "auth");
                anyone.addProperty(ACP.agent, ACP.AuthenticatedAgent);
                allow.addProperty(ACP.anyOf, anyone);
                allow.addProperty(ACP.allow, ACL.Read);

                Resource members = control(m, ROOT, ACP.memberAccessControl, "members");
                Resource deny = policy(m, members, "rootDeny");
                Resource bob = matcher(m, "bob");
                bob.addProperty(ACP.agent, m.createResource(BOB));
                deny.addProperty(ACP.anyOf, bob);
                deny.addProperty(ACP.deny, ACL.Read);
            });
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX));
            assertEquals(Set.of(), modes(BOB_CTX),
                    "a deny inherited from the storage root beats a local allow");
        }

        /** A parent cycle must terminate on MAX_DEPTH rather than spin forever. */
        @Test
        void aParentCycleTerminates() {
            sys(m -> {
                m.createResource(TARGET).addProperty(LWSX.parent, m.createResource(PARENT));
                m.createResource(PARENT).addProperty(LWSX.parent, m.createResource(TARGET));
            });
            assertEquals(Set.of(), modes(ALICE_CTX), "the walk is depth-bounded, not endless");
        }
    }

    // ------------------------------------------------------------- time-boxing

    @Nested
    class TimeBoxedGrants {

        private void grantValid(String from, String until) {
            acp(m -> {
                Resource ac = control(m, TARGET, ACP.accessControl, "own");
                Resource acr = acrNode(m, TARGET, "own");
                if (from != null) {
                    acr.addProperty(ResourceFactory.createProperty("https://schema.org/validFrom"),
                            m.createLiteral(from));
                }
                if (until != null) {
                    acr.addProperty(ResourceFactory.createProperty("https://schema.org/expires"),
                            m.createLiteral(until));
                }
                Resource p = policy(m, ac, "timed");
                Resource mt = matcher(m, "auth");
                mt.addProperty(ACP.agent, ACP.AuthenticatedAgent);
                p.addProperty(ACP.anyOf, mt);
                p.addProperty(ACP.allow, ACL.Read);
            });
        }

        private static String iso(long amount, ChronoUnit unit) {
            return Instant.now().plus(amount, unit).toString();
        }

        @Test
        void anAcrWithNoBoundsIsAlwaysActive() {
            grantValid(null, null);
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX));
        }

        @Test
        void aGrantInsideItsWindowApplies() {
            grantValid(iso(-1, ChronoUnit.HOURS), iso(1, ChronoUnit.HOURS));
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX));
        }

        @Test
        void aGrantNotYetValidContributesNothing() {
            grantValid(iso(1, ChronoUnit.HOURS), null);
            assertEquals(Set.of(), modes(ALICE_CTX));
        }

        @Test
        void anExpiredGrantContributesNothing() {
            grantValid(null, iso(-1, ChronoUnit.HOURS));
            assertEquals(Set.of(), modes(ALICE_CTX),
                    "expiry is enforced at evaluation, with no revocation sweep");
        }

        @Test
        void anUnparseableBoundFailsClosed() {
            grantValid(null, "whenever-i-feel-like-it");
            assertEquals(Set.of(), modes(ALICE_CTX),
                    "an unreadable expiry must not be read as 'no expiry'");
        }

        @Test
        void aLocalDateTimeBoundIsAccepted() {
            grantValid(null, java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                    .plusHours(1).toString());
            assertEquals(Set.of(AccessMode.READ), modes(ALICE_CTX));
        }

        /**
         * Known defect F058. {@code activeNow} reads the bound with {@code Statement.getString()},
         * which throws when the object is not a literal — so an ACR carrying a non-literal
         * {@code schema:expires} makes every evaluation of that resource throw, permanently
         * 500-ing it rather than failing closed.
         *
         * <p>This pins the CURRENT behaviour so the defect cannot be forgotten. When F058 is
         * fixed, this test should start failing: change it to assert {@code Set.of()}, matching
         * {@link #anUnparseableBoundFailsClosed()}.
         */
        @Test
        void aNonLiteralBoundThrowsInsteadOfFailingClosed_knownDefectF058() {
            acp(m -> {
                Resource ac = control(m, TARGET, ACP.accessControl, "own");
                acrNode(m, TARGET, "own").addProperty(
                        ResourceFactory.createProperty("https://schema.org/expires"),
                        m.createResource("urn:not:a:literal"));
                Resource p = policy(m, ac, "timed");
                Resource mt = matcher(m, "auth");
                mt.addProperty(ACP.agent, ACP.AuthenticatedAgent);
                p.addProperty(ACP.anyOf, mt);
                p.addProperty(ACP.allow, ACL.Read);
            });
            assertThrows(LiteralRequiredException.class, () -> modes(ALICE_CTX),
                    "F058: a non-literal bound throws rather than failing closed");
        }
    }

    // ------------------------------------------------------------ memoisation

    @Nested
    class Memoisation {

        /**
         * The engine's memo is keyed by resource URI <em>alone</em>, so one engine instance must
         * never be reused across agents. The module compensates by building a fresh
         * {@code AcpEngine} inside every write transaction, and the comments at those call sites
         * say so. This test exists so that discipline has a reason on record: if the memo ever
         * became agent-aware the assertion below would fail, and the fresh-engine requirement
         * could then be relaxed deliberately rather than by accident.
         */
        @Test
        void theMemoIsKeyedByUriAloneSoAnEngineIsSingleAgentOnly() {
            grantOnTarget(ResourceFactory.createResource(ALICE), ACL.Read);

            store.read(() -> {
                AcpEngine shared = new AcpEngine(store);
                assertTrue(shared.modes(ALICE_CTX, TARGET).contains(AccessMode.READ));
                assertTrue(shared.modes(BOB_CTX, TARGET).contains(AccessMode.READ),
                        "Bob receives Alice's memoised decision -- this is why every write path "
                        + "constructs a fresh AcpEngine");
            });

            assertFalse(modes(BOB_CTX).contains(AccessMode.READ),
                    "a fresh engine decides correctly for Bob");
        }

        @Test
        void allowsAgreesWithModes() {
            grantOnTarget(ACP.AuthenticatedAgent, ACL.Read, ACL.Write);
            store.read(() -> {
                AcpEngine e = new AcpEngine(store);
                assertTrue(e.allows(ALICE_CTX, TARGET, AccessMode.READ));
                assertTrue(e.allows(ALICE_CTX, TARGET, AccessMode.WRITE));
                assertTrue(e.allows(ALICE_CTX, TARGET, AccessMode.APPEND));
                assertFalse(e.allows(ALICE_CTX, TARGET, AccessMode.CONTROL));
            });
        }
    }
}
