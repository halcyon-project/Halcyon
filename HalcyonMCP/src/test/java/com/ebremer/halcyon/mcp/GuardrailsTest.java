package com.ebremer.halcyon.mcp;

import java.util.List;
import org.apache.jena.query.Query;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP-5 pinned mutation-style: each property of
 * {@link Guardrails#readOnlyQuery} has a test that fails if that guard is
 * deleted — the C2 lesson must not be re-learnable.
 */
class GuardrailsTest {

    @Test
    void missingLimitIsInjected() {
        Query q = Guardrails.readOnlyQuery("SELECT * WHERE { ?s ?p ?o }", 100);
        assertEquals(100, q.getLimit(), "an unbounded SELECT must not leave this method unbounded");
    }

    @Test
    void oversizedLimitIsClampedDown() {
        Query q = Guardrails.readOnlyQuery("SELECT * WHERE { ?s ?p ?o } LIMIT 999999", 100);
        assertEquals(100, q.getLimit());
    }

    @Test
    void smallerLimitIsKept() {
        Query q = Guardrails.readOnlyQuery("SELECT * WHERE { ?s ?p ?o } LIMIT 10", 100);
        assertEquals(10, q.getLimit(), "a caller asking for less must get less");
    }

    @Test
    void updatesDoNotParseHere() {
        for (String update : List.of(
                "INSERT DATA { <urn:a> <urn:b> <urn:c> }",
                "DELETE WHERE { ?s ?p ?o }",
                "DROP ALL",
                "LOAD <http://evil.example/data.ttl>",
                "CLEAR DEFAULT")) {
            assertThrows(IllegalArgumentException.class,
                    () -> Guardrails.readOnlyQuery(update, 100),
                    "update form must be refused: " + update);
        }
    }

    @Test
    void garbageIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> Guardrails.readOnlyQuery("SELECT WHERE oops", 100));
    }

    @Test
    void serviceIsRefusedTopLevelAndInSubqueries() {
        assertThrows(IllegalArgumentException.class, () -> Guardrails.readOnlyQuery(
                "SELECT * WHERE { SERVICE <http://internal:8080/secret> { ?s ?p ?o } }", 100),
                "SERVICE is an SSRF primitive and must be refused");
        assertThrows(IllegalArgumentException.class, () -> Guardrails.readOnlyQuery(
                "SELECT * WHERE { { SELECT ?s WHERE { SERVICE <http://internal> { ?s ?p ?o } } } }",
                100),
                "SERVICE hidden in a subquery must be refused too");
    }

    @Test
    void askConstructAndDescribeAreAcceptedReadForms() {
        assertTrue(Guardrails.readOnlyQuery("ASK { ?s ?p ?o }", 100).isAskType());
        assertEquals(100, Guardrails.readOnlyQuery(
                "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }", 100).getLimit(),
                "CONSTRUCT is row-producing and gets the cap");
        assertEquals(100, Guardrails.readOnlyQuery("DESCRIBE <urn:x>", 100).getLimit(),
                "DESCRIBE (even with no pattern) gets the cap");
    }
}
