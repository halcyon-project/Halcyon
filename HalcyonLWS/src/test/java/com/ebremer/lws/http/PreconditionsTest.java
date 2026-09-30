package com.ebremer.lws.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins how a state-changing request's preconditions are evaluated.
 *
 * <p>lws10-core, since #228: clients SHOULD make writes conditional, and a precondition that is
 * sent and fails is a 412 — nothing more is demanded of either side. So an unconditional write
 * proceeds, and everything else is RFC 9110 §13: {@code If-Match} first and by strong comparison,
 * {@code If-None-Match} only in its absence and by weak comparison, a false one a 412.
 */
class PreconditionsTest {

    private static final String TAG = "\"v1\"";

    @Test
    void anUnconditionalWriteProceeds() {
        assertDoesNotThrow(() -> Preconditions.evaluate(request(Map.of()), TAG));
        assertDoesNotThrow(() -> Preconditions.evaluate(request(Map.of()), null));
    }

    @Test
    void aCurrentIfMatchHolds() {
        assertDoesNotThrow(() -> Preconditions.evaluate(request(Map.of("If-Match", TAG)), TAG));
        assertDoesNotThrow(() -> Preconditions.evaluate(
                request(Map.of("If-Match", "\"v0\", " + TAG)), TAG));
    }

    @Test
    void aStaleIfMatchIsPreconditionFailed() {
        Problem p = assertThrows(Problem.class,
                () -> Preconditions.evaluate(request(Map.of("If-Match", "\"v0\"")), TAG));
        assertEquals(412, p.status());
    }

    @Test
    void ifMatchComparesStrongly() {
        // RFC 9110 §13.1.1: a weak entity tag never satisfies If-Match.
        assertEquals(412, assertThrows(Problem.class,
                () -> Preconditions.evaluate(request(Map.of("If-Match", "W/" + TAG)), TAG))
                .status());
    }

    @Test
    void ifMatchStarNeedsARepresentation() {
        assertDoesNotThrow(() -> Preconditions.evaluate(request(Map.of("If-Match", "*")), TAG));
        assertEquals(412, assertThrows(Problem.class,
                () -> Preconditions.evaluate(request(Map.of("If-Match", "*")), null)).status());
    }

    @Test
    void ifMatchOnAResourceThatDoesNotExistIsPreconditionFailed() {
        assertEquals(412, assertThrows(Problem.class,
                () -> Preconditions.evaluate(request(Map.of("If-Match", TAG)), null)).status());
    }

    @Test
    void ifNoneMatchStarIsCreateButNeverOverwrite() {
        assertDoesNotThrow(() -> Preconditions.evaluate(request(Map.of("If-None-Match", "*")),
                null));
        assertEquals(412, assertThrows(Problem.class,
                () -> Preconditions.evaluate(request(Map.of("If-None-Match", "*")), TAG))
                .status());
    }

    @Test
    void ifNoneMatchComparesWeakly() {
        assertEquals(412, assertThrows(Problem.class,
                () -> Preconditions.evaluate(request(Map.of("If-None-Match", "W/" + TAG)), TAG))
                .status());
        assertDoesNotThrow(() -> Preconditions.evaluate(
                request(Map.of("If-None-Match", "\"v0\"")), TAG));
    }

    @Test
    void ifMatchIsEvaluatedAndIfNoneMatchThenIgnored() {
        // RFC 9110 §13.2.2 step 3 runs only "when If-Match is not present".
        assertDoesNotThrow(() -> Preconditions.evaluate(
                request(Map.of("If-Match", TAG, "If-None-Match", "*")), TAG));
    }

    @Test
    void requirePreconditionRefusesAnUnconditionalWrite() {
        assertEquals(428, assertThrows(Problem.class,
                () -> Preconditions.requirePrecondition(request(Map.of()), TAG)).status());
        assertEquals(412, assertThrows(Problem.class,
                () -> Preconditions.requirePrecondition(request(Map.of("If-Match", "\"v0\"")),
                        TAG)).status());
        assertDoesNotThrow(() -> Preconditions.requirePrecondition(
                request(Map.of("If-Match", TAG)), TAG));
    }

    @Test
    void aReadRevalidatesWeakly() {
        assertTrue(Preconditions.isNotModified(request(Map.of("If-None-Match", "W/" + TAG)), TAG,
                null));
        assertFalse(Preconditions.isNotModified(request(Map.of("If-None-Match", "\"v0\"")), TAG,
                null));
    }

    private static HttpServletRequest request(Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(PreconditionsTest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, (proxy, m, args) -> switch (m.getName()) {
                    case "getHeader" -> headers.get((String) args[0]);
                    case "toString" -> "request" + headers;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }
}
