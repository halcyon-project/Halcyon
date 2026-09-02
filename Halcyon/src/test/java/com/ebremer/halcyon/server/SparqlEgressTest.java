package com.ebremer.halcyon.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins how the SPARQL {@code SERVICE} egress allow-list reads this deployment's own host out of
 * {@code :ProxyHostName}.
 *
 * <p>This is the one entry on that allow-list that is not written by the operator by hand, so a
 * parsing slip here has a security consequence in both directions: fail to extract the host and
 * self-federation breaks (every LWS resource is a SPARQL endpoint on this origin), extract the
 * wrong one and something that is not this server becomes reachable from a caller's query.
 *
 * <p>The setting is documented as a full origin ({@code https://localhost:8888}), which is what
 * {@code Halcyon/settings.ttl} carries, but the bare {@code host:port} and {@code host} spellings
 * are tolerated because nothing validates the setting at startup.
 */
class SparqlEgressTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "https://localhost:8888,          localhost",
        "http://localhost:8888,           localhost",
        "https://halcyon.example.org,     halcyon.example.org",
        "https://halcyon.example.org/,    halcyon.example.org",
        "https://Halcyon.Example.ORG:443, halcyon.example.org",
        "localhost:8888,                  localhost",
        "localhost,                       localhost",
        "halcyon.example.org,             halcyon.example.org",
        "halcyon.example.org/base,        halcyon.example.org",
    })
    void theOwnHostIsExtractedFromTheConfiguredOrigin(String origin, String expected) {
        assertEquals(expected, SparqlEgress.hostOf(origin));
    }

    @Test
    void anAbsentOriginYieldsNoAllowedHost() {
        assertNull(SparqlEgress.hostOf(null));
        assertNull(SparqlEgress.hostOf(""));
        assertNull(SparqlEgress.hostOf("   "));
    }

    /**
     * An IPv6 literal must not have its last colon-group mistaken for a port and chopped off —
     * that would leave a truncated string that matches nothing, silently dropping the deployment's
     * own host from the allow-list.
     *
     * <p>The bracketed form is the right answer rather than an artefact: {@code URI.getHost()}
     * returns {@code [::1]} for an IPv6 authority, and {@code SsrfGuard} compares a target's
     * {@code getHost()} against this allow-list, so both sides carry the brackets.
     */
    @Test
    void anIpv6OriginKeepsItsAddress() {
        assertEquals("[::1]", SparqlEgress.hostOf("[::1]:8888"));
        assertEquals("[::1]", SparqlEgress.hostOf("https://[::1]:8888"));
    }
}
