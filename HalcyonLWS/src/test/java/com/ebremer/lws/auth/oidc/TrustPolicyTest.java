package com.ebremer.lws.auth.oidc;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the identity-provider allow/deny policy.
 *
 * <p>This is what gives {@code acp:AuthenticatedAgent} a defensible meaning. Halcyon's WebID login
 * takes the OpenID Provider from the caller's OWN WebID document, so without a policy here
 * "authenticated" means "anyone who can host a WebID document and run an OP" — correct for open
 * federation, useless as a trust tier. The three states below are the whole contract, and the
 * allow-all default is the one that matters most: adding this class must not change the behaviour
 * of a deployment that has configured nothing.
 */
class TrustPolicyTest {

    private static TrustPolicy policy(List<String> allow, List<String> deny) {
        return TrustPolicy.forHosts(allow, deny);
    }

    private static TrustPolicy issuers(List<String> allow, List<String> deny) {
        return TrustPolicy.forIssuers(allow, deny);
    }

    // -------------------------------------------------------------- default

    @Test
    void nothingConfiguredPermitsEverything() {
        TrustPolicy p = policy(List.of(), List.of());
        assertTrue(p.isAllowAll());
        assertTrue(p.permits("https://anyone.example/realms/x"));
        assertTrue(p.permits("https://evil.example"));
        assertDoesNotThrow(() -> p.require("issuer", "https://anyone.example"));
    }

    @Test
    void theAllowAllConstantAgreesWithAnEmptyPolicy() {
        assertTrue(TrustPolicy.ALLOW_ALL.isAllowAll());
        assertTrue(TrustPolicy.ALLOW_ALL.permits("https://anything.example"));
    }

    /** A null-ish configuration is the unset case, not an empty allow-list that denies everyone. */
    @Test
    void nullListsAreTreatedAsUnset() {
        assertTrue(TrustPolicy.forHosts(null, null).isAllowAll());
        assertTrue(TrustPolicy.forHosts(List.of("  "), List.of("")).isAllowAll(),
                "blank entries are not a configuration");
    }

    // ------------------------------------------------------------ allow list

    @Test
    void aNonEmptyAllowListIsExclusive() {
        TrustPolicy p = policy(List.of("id.example.org"), List.of());
        assertFalse(p.isAllowAll());
        assertTrue(p.permits("https://id.example.org/realms/halcyon"));
        assertFalse(p.permits("https://other.example/realms/x"),
                "naming anything makes the list the whole permitted set");
    }

    @Test
    void anAllowListEntryMayBeWrittenAsAFullUrl() {
        TrustPolicy p = policy(List.of("https://id.example.org/realms/halcyon"), List.of());
        assertTrue(p.permits("https://id.example.org/realms/other"),
                "an operator pasting the :AuthServer URL should still get a host match");
    }

    @ParameterizedTest(name = "host match: {0} against id.example.org")
    @CsvSource({
        "https://id.example.org,              true",
        "https://ID.EXAMPLE.ORG/realms/x,     true",
        "https://id.example.org:8443/realms,  true",
        "https://evil.org,                    false",
        "https://id.example.org.evil.com,     false",
        "https://notid.example.org,           false",
    })
    void hostsAreComparedNotPrefixes(String url, boolean expected) {
        assertTrue(policy(List.of("id.example.org"), List.of()).permits(url) == expected, url);
    }

    // ------------------------------------------------------------- deny list

    @Test
    void denyWinsOverAllowAll() {
        TrustPolicy p = policy(List.of(), List.of("evil.example"));
        assertFalse(p.isAllowAll());
        assertFalse(p.permits("https://evil.example/realms/x"));
        assertTrue(p.permits("https://anyone-else.example"),
                "a deny list alone still permits everything it does not name");
    }

    @Test
    void denyWinsOverAnExplicitAllow() {
        TrustPolicy p = policy(List.of("id.example.org"), List.of("id.example.org"));
        assertFalse(p.permits("https://id.example.org/realms/x"),
                "denied beats allowed, the same way acp:deny beats acp:allow");
    }

    // -------------------------------------------------------------- wildcard

    @Test
    void aWildcardCoversSubdomainsButNotTheBareDomain() {
        TrustPolicy p = policy(List.of("*.example.org"), List.of());
        assertTrue(p.permits("https://id.example.org/realms/x"));
        assertTrue(p.permits("https://a.b.example.org"));
        assertFalse(p.permits("https://example.org"),
                "a wildcard that silently covered the parent would be a trap; list it too");
        assertFalse(p.permits("https://notexample.org"));
    }

    // ---------------------------------------------------------- fail closed

    @Test
    void anUnparseableValueIsRefusedOnceAPolicyExists() {
        TrustPolicy p = policy(List.of("id.example.org"), List.of());
        assertFalse(p.permits("not a url"));
        assertFalse(p.permits(""));
        assertFalse(p.permits(null));
        assertTrue(TrustPolicy.ALLOW_ALL.permits("not a url"),
                "...but an unconfigured deployment still behaves exactly as before");
    }

    @Test
    void theRefusalNamesTheHostAndWhatWasPermitted() {
        TrustPolicy p = policy(List.of("id.example.org"), List.of());
        TrustPolicy.RefusedException e = assertThrows(TrustPolicy.RefusedException.class,
                () -> p.require("OpenID Provider", "https://evil.example/realms/x"));
        assertTrue(e.getMessage().contains("OpenID Provider"), e.getMessage());
        assertTrue(e.getMessage().contains("evil.example"), e.getMessage());
        assertTrue(e.getMessage().contains("id.example.org"),
                "the message should say what IS permitted: " + e.getMessage());
    }

    @Test
    void anIpv6IssuerKeepsItsAddress() {
        assertEquals0("[::1]", TrustPolicy.hostOf("https://[::1]:8443/realms/x"));
        assertEquals0("[::1]", TrustPolicy.hostOf("[::1]:8443"));
    }

    private static void assertEquals0(String expected, String actual) {
        assertTrue(expected.equals(actual), "expected " + expected + " but was " + actual);
    }

    // ------------------------------------------------------- issuer matching

    /**
     * The reason this class matches issuers as whole URIs rather than by host. Two realms on one
     * identity server are different trust domains; a host-level list would accept both while
     * reading as though it accepted one.
     */
    @Test
    void twoRealmsOnOneHostAreDifferentIssuers() {
        TrustPolicy p = issuers(List.of("https://id.example.org/auth/realms/Halcyon"), List.of());
        assertTrue(p.permits("https://id.example.org/auth/realms/Halcyon"));
        assertFalse(p.permits("https://id.example.org/auth/realms/Anyone"),
                "a second realm on the same host must NOT be accepted");
        assertFalse(p.permits("https://id.example.org"),
                "nor the bare host");
    }

    @ParameterizedTest(name = "issuer normalisation: {0}")
    @CsvSource({
        "https://id.example.org/realms/X,        true",
        "https://id.example.org/realms/X/,       true",
        "https://ID.EXAMPLE.ORG/realms/X,        true",
        "HTTPS://id.example.org/realms/X,        true",
        "https://id.example.org:443/realms/X,    true",
        "https://id.example.org:8443/realms/X,   false",
        "http://id.example.org/realms/X,         false",
        "https://id.example.org/realms/x,        false",
        "https://evil.example/realms/X,          false",
    })
    void issuersAreComparedAfterNormalisation(String candidate, boolean expected) {
        TrustPolicy p = issuers(List.of("https://id.example.org/realms/X"), List.of());
        assertTrue(p.permits(candidate) == expected, candidate);
    }

    /** Case matters in a path and must not be folded: /realms/X is not /realms/x. */
    @Test
    void issuerPathCaseIsSignificant() {
        assertFalse(issuers(List.of("https://id.example.org/realms/Halcyon"), List.of())
                .permits("https://id.example.org/realms/halcyon"));
    }

    /**
     * A wildcard is a host concept and is refused for issuers — loudly. Dropping it instead would
     * leave an EMPTY allow list, which is indistinguishable from unconfigured and therefore permits
     * everything: a restriction that silently becomes no restriction. Same for a bare host, which
     * is the likelier typo.
     */
    @Test
    void anUnusableIssuerEntryFailsLoudlyRatherThanSilentlyPermittingEverything() {
        for (String bad : List.of("*.example.org", "id.example.org", "not a uri",
                "https://id.example.org/realms/X?a=b")) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> issuers(List.of(bad), List.of()), bad);
            assertTrue(e.getMessage().contains(bad), e.getMessage());
        }
    }

    /** The same trap on the host side: a value with no readable host must not be dropped. */
    @Test
    void anUnusableHostEntryAlsoFailsLoudly() {
        assertThrows(IllegalStateException.class, () -> policy(List.of("://"), List.of()));
    }

    @Test
    void denyWinsForIssuersToo() {
        TrustPolicy p = issuers(
                List.of("https://id.example.org/realms/X", "https://id.example.org/realms/Y"),
                List.of("https://id.example.org/realms/Y"));
        assertTrue(p.permits("https://id.example.org/realms/X"));
        assertFalse(p.permits("https://id.example.org/realms/Y"));
    }

    @Test
    void aValueThatIsNotAnIssuerIdentifierIsRefused() {
        TrustPolicy p = issuers(List.of("https://id.example.org/realms/X"), List.of());
        assertFalse(p.permits("id.example.org"), "no scheme");
        assertFalse(p.permits("https://id.example.org/realms/X?a=b"), "query is not part of an issuer");
        assertFalse(p.permits("https://id.example.org/realms/X#f"), "nor a fragment");
        assertTrue(TrustPolicy.ALLOW_ALL.permits("nonsense"),
                "...but an unconfigured deployment is still unchanged");
    }

    @Test
    void unconfiguredIssuerPolicyPermitsEverything() {
        TrustPolicy p = issuers(List.of(), List.of());
        assertTrue(p.isAllowAll());
        assertTrue(p.permits("https://anyone.example/realms/whatever"));
    }
}
