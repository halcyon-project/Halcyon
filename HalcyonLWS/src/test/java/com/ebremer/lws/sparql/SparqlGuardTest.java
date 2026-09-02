package com.ebremer.lws.sparql;

import java.util.List;
import java.util.Set;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the egress policy for client-supplied SPARQL.
 *
 * <p>F016: the previous federation ban walked the parsed {@code Element} tree, which does not
 * enter expressions — so a {@code SERVICE} hidden inside {@code FILTER EXISTS},
 * {@code FILTER NOT EXISTS} or {@code BIND(EXISTS{...})} was invisible to it while plain,
 * subquery, {@code OPTIONAL}, {@code MINUS}, {@code UNION} and {@code GRAPH} nesting were all
 * caught. The ban therefore read as working while being one keyword away from bypass, which is the
 * worst state for a security check to be in. {@link #serviceIsFoundHoweverItIsNested} is that
 * measurement turned into a test: the three EXISTS rows are the ones that used to return false.
 *
 * <p>The nesting table is deliberately exhaustive rather than minimal. A future change to how
 * detection works — a different walker, a syntax-level shortcut for speed — has to keep every row
 * true, and the rows that matter are not the obvious ones.
 */
class SparqlGuardTest {

    private static Query q(String s) {
        return QueryFactory.create(s);
    }

    // ------------------------------------------------------------- detection

    @ParameterizedTest(name = "SERVICE is detected: {0}")
    @CsvSource(delimiter = '|', value = {
        "plain                 | SELECT * { SERVICE <http://evil.example/> { ?s ?p ?o } }",
        "subquery              | SELECT * { { SELECT * { SERVICE <http://evil.example/> { ?s ?p ?o } } } }",
        "FILTER EXISTS         | SELECT * { ?s ?p ?o FILTER EXISTS { SERVICE <http://evil.example/> { ?a ?b ?c } } }",
        "FILTER NOT EXISTS     | SELECT * { ?s ?p ?o FILTER NOT EXISTS { SERVICE <http://evil.example/> { ?a ?b ?c } } }",
        "BIND(EXISTS)          | SELECT * { ?s ?p ?o BIND(EXISTS { SERVICE <http://evil.example/> { ?a ?b ?c } } AS ?x) }",
        "MINUS                 | SELECT * { ?s ?p ?o MINUS { SERVICE <http://evil.example/> { ?a ?b ?c } } }",
        "OPTIONAL              | SELECT * { ?s ?p ?o OPTIONAL { SERVICE <http://evil.example/> { ?a ?b ?c } } }",
        "UNION                 | SELECT * { { ?s ?p ?o } UNION { SERVICE <http://evil.example/> { ?a ?b ?c } } }",
        "GRAPH                 | SELECT * { GRAPH ?g { SERVICE <http://evil.example/> { ?a ?b ?c } } }",
        "nested twice          | SELECT * { ?s ?p ?o FILTER NOT EXISTS { OPTIONAL { SERVICE <http://evil.example/> { ?a ?b ?c } } } }",
        "SILENT                | SELECT * { SERVICE SILENT <http://evil.example/> { ?s ?p ?o } }",
        "ASK query             | ASK { SERVICE <http://evil.example/> { ?s ?p ?o } }",
        "CONSTRUCT query       | CONSTRUCT { ?s ?p ?o } WHERE { SERVICE <http://evil.example/> { ?s ?p ?o } }",
    })
    void serviceIsFoundHoweverItIsNested(String label, String sparql) {
        assertTrue(SparqlGuard.federates(q(sparql)), label + ": SERVICE must be detected");
        assertThrows(SparqlGuard.RefusedException.class,
                () -> SparqlGuard.refuseFederation(q(sparql)), label);
    }

    @ParameterizedTest(name = "no false positive: {0}")
    @CsvSource(delimiter = '|', value = {
        "plain select      | SELECT * { ?s ?p ?o }",
        "filter exists     | SELECT * { ?s ?p ?o FILTER EXISTS { ?a ?b ?c } }",
        "not exists        | SELECT * { ?s ?p ?o FILTER NOT EXISTS { ?a ?b ?c } }",
        "bind exists       | SELECT * { ?s ?p ?o BIND(EXISTS { ?a ?b ?c } AS ?x) }",
        "union optional    | SELECT * { { ?s ?p ?o } UNION { OPTIONAL { ?a ?b ?c } } }",
        "subquery          | SELECT * { { SELECT * { ?s ?p ?o } } }",
        "values            | SELECT * { VALUES ?s { <urn:a> } ?s ?p ?o }",
    })
    void aQueryThatDoesNotFederateIsLeftAlone(String label, String sparql) {
        assertFalse(SparqlGuard.federates(q(sparql)), label);
        assertDoesNotThrow(() -> SparqlGuard.refuseFederation(q(sparql)), label);
        assertDoesNotThrow(() -> SparqlGuard.checkEgress(q(sparql), Set.of()), label);
    }

    @Test
    void everyTargetIsReportedAndDuplicatesArePreserved() {
        List<String> targets = SparqlGuard.serviceTargets(q(
                "SELECT * { SERVICE <http://a.example/> { ?s ?p ?o } "
                + "OPTIONAL { SERVICE <http://b.example/> { ?a ?b ?c } } "
                + "FILTER EXISTS { SERVICE <http://c.example/> { ?d ?e ?f } } }"));
        assertEquals(3, targets.size(), "all three targets, including the one inside EXISTS");
        assertTrue(targets.containsAll(
                List.of("http://a.example/", "http://b.example/", "http://c.example/")));
    }

    // ---------------------------------------------------------------- egress

    /**
     * The internal addresses an SSRF is actually aimed at. IP literals rather than host names, so
     * the test does not depend on DNS.
     */
    @ParameterizedTest(name = "egress refused: {0}")
    @CsvSource({
        "loopback,            http://127.0.0.1:8888/rdf2",
        "cloud metadata,      http://169.254.169.254/latest/meta-data/",
        "private 10/8,        http://10.0.0.5/sparql",
        "private 192.168/16,  http://192.168.1.10/sparql",
        "this network 0/8,    http://0.0.0.0/sparql",
        "IPv6 loopback,       http://[::1]/sparql",
    })
    void anInternalServiceTargetIsRefused(String label, String url) {
        Query query = q("SELECT * { SERVICE <" + url + "> { ?s ?p ?o } }");
        SparqlGuard.RefusedException e = assertThrows(SparqlGuard.RefusedException.class,
                () -> SparqlGuard.checkEgress(query, Set.of()), label);
        assertTrue(e.getMessage().contains("SERVICE target refused"), e.getMessage());
    }

    /**
     * The bypass, end to end: an internal target hidden inside {@code FILTER EXISTS} must be
     * refused by the egress check, not merely detectable in isolation.
     */
    @Test
    void anInternalTargetHiddenInsideExistsIsStillRefused() {
        Query query = q("SELECT * { ?s ?p ?o FILTER EXISTS { "
                + "SERVICE <http://169.254.169.254/latest/meta-data/> { ?a ?b ?c } } }");
        assertThrows(SparqlGuard.RefusedException.class,
                () -> SparqlGuard.checkEgress(query, Set.of()));
    }

    /**
     * Self-federation is a deliberate feature — every LWS resource is a SPARQL endpoint on this
     * server's own origin — so an allow-listed host must survive the check. {@code SsrfGuard}
     * short-circuits on the allow-list before resolving, which is also what keeps this test off
     * the network.
     */
    @Test
    void anAllowListedHostIsPermittedSoSelfFederationKeepsWorking() {
        Query query = q("SELECT * { SERVICE <https://localhost:8888/W3Clws/x> { ?s ?p ?o } }");
        assertThrows(SparqlGuard.RefusedException.class,
                () -> SparqlGuard.checkEgress(query, Set.of()),
                "loopback is refused when it is not allow-listed");
        assertDoesNotThrow(() -> SparqlGuard.checkEgress(query, Set.of("localhost")),
                "...and permitted when the operator allows that host");
    }

    /**
     * A variable endpoint passes the STATIC check, deliberately. Refusing it here was the first
     * attempt and it broke a real query: every LWS resource is itself a SPARQL endpoint, so
     * {@code graph ?g { ... } service ?g { ... }} is the natural way to ask one question of every
     * matching resource, and that is the shape the refusal killed. The resolved target is checked
     * instead at execution time, per binding, by the app tier's
     * {@code SparqlServiceEgressExecutor}.
     */
    @Test
    void aVariableServiceEndpointIsDeferredToExecutionTimeNotRefused() {
        Query query = q("SELECT * { ?s <urn:endpoint> ?e . SERVICE ?e { ?a ?b ?c } }");
        assertDoesNotThrow(() -> SparqlGuard.checkEgress(query, Set.of()),
                "a variable endpoint has no target to check statically");
        assertTrue(SparqlGuard.serviceTargets(query).contains(null),
                "but it is reported as null rather than silently dropped, so a caller that must "
                + "fail closed still can");
        assertTrue(SparqlGuard.federates(query),
                "and it still counts as federation, so the MCP ban still refuses it");
    }

    /** The shape that regressed: federate to each graph the pattern matched. */
    @Test
    void theGraphThenServiceIdiomIsAccepted() {
        Query query = q("SELECT DISTINCT ?g WHERE { GRAPH ?g { ?s <urn:p> \"x\" } "
                + "SERVICE ?g { ?sx <urn:w> ?w } } LIMIT 2000");
        assertDoesNotThrow(() -> SparqlGuard.checkEgress(query, Set.of()));
    }

    @Test
    void aNonHttpServiceSchemeIsRefused() {
        Query query = q("SELECT * { SERVICE <file:///etc/passwd> { ?s ?p ?o } }");
        assertThrows(SparqlGuard.RefusedException.class,
                () -> SparqlGuard.checkEgress(query, Set.of()));
    }

    @Test
    void aQueryWithNoPatternIsHandled() {
        assertFalse(SparqlGuard.federates(q("DESCRIBE <urn:a>")));
        assertDoesNotThrow(() -> SparqlGuard.checkEgress(q("DESCRIBE <urn:a>"), Set.of()));
    }
}
