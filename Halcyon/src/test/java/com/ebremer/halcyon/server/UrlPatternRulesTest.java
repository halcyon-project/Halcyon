package com.ebremer.halcyon.server;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the rules that keep a security filter from being registered on a pattern it cannot match.
 *
 * <p>F008 / C24. {@code URLControl.getSecuredURLs()} carried {@code "/iiif*&#47;"} to guard the IIIF
 * servlet mounted at {@code /iiif/*}, and {@code "/invalidateSession"} to guard a servlet mounted at
 * {@code /invalidateSession/*}. Neither works, and neither said so: Jetty parses a <em>servlet</em>
 * mapping through {@code ServletPathSpec}'s constructor and rejects an illegal one at startup, but
 * keeps a <em>filter</em> mapping as a raw string matched by a static method — so an illegal filter
 * pattern is accepted silently and degrades to an exact match on its own literal characters. The
 * endpoint is then unguarded with no error at any point, which is why these rules throw rather than
 * warn: the defect's whole character is that it produced no signal.
 */
class UrlPatternRulesTest {

    // ------------------------------------------------------------- legality

    /** The exact string that left {@code /iiif/*} unguarded, plus its relatives. */
    @ParameterizedTest(name = "illegal: {0}")
    @ValueSource(strings = {"/iiif*/", "/a*/b", "/*/tail"})
    void aGlobAnywhereButTheEndIsRefused(String pattern) {
        assertThrows(UrlPatternRules.IllegalPatternException.class,
                () -> UrlPatternRules.assertLegal("test", List.of(pattern)));
    }

    /**
     * Jetty accepts {@code "/f*"} as an EXACT spec and only logs a "suspicious URL pattern" warning
     * from a constructor filters never reach — so legality alone is not enough. An exact spec
     * containing a glob is always a mistyped prefix spec.
     */
    @ParameterizedTest(name = "exact-with-glob: {0}")
    @ValueSource(strings = {"/f*", "/iiif*", "/admin*"})
    void anExactSpecContainingAGlobIsRefused(String pattern) {
        UrlPatternRules.IllegalPatternException e = assertThrows(
                UrlPatternRules.IllegalPatternException.class,
                () -> UrlPatternRules.assertLegal("test", List.of(pattern)));
        assertTrue(e.getMessage().contains("did you mean"),
                "the message should suggest the prefix form: " + e.getMessage());
    }

    @ParameterizedTest(name = "legal: {0}")
    @ValueSource(strings = {"/iiif/*", "/invalidateSession/*", "/callback", "/skunkworks/yay",
        "*.jsp", "/user/account"})
    void theFourLegalFormsAreAccepted(String pattern) {
        assertDoesNotThrow(() -> UrlPatternRules.assertLegal("test", List.of(pattern)));
    }

    /**
     * The distinction that broke a real launch. {@code "/"} is the default mapping: correct for a
     * dispatcher servlet, never right for a secured-URL list. Applying the strict rule to every
     * registration rejected Spring's own {@code dispatcherServlet} and the application refused to
     * start, so legality and guardability are separate questions with separate methods.
     */
    @Test
    void theDefaultMappingIsLegalButNotGuardable() {
        assertDoesNotThrow(() -> UrlPatternRules.assertLegal("test", List.of("/")),
                "a dispatcher servlet is legitimately mapped on \"/\"");
        assertThrows(UrlPatternRules.IllegalPatternException.class,
                () -> UrlPatternRules.assertGuardable("test", List.of("/")),
                "but a secured-URL list must not cover the whole application");
    }

    @Test
    void theCatchAllPrefixIsLegalEverywhere() {
        assertDoesNotThrow(() -> UrlPatternRules.assertLegal("test", List.of("/*")),
                "Wicket's filter is mapped on \"/*\"");
        assertDoesNotThrow(() -> UrlPatternRules.assertGuardable("test", List.of("/*")));
    }

    @Test
    void blankAndDuplicatePatternsAreRefused() {
        assertThrows(UrlPatternRules.IllegalPatternException.class,
                () -> UrlPatternRules.assertLegal("test", List.of("  ")));
        assertThrows(UrlPatternRules.IllegalPatternException.class,
                () -> UrlPatternRules.assertGuardable("test", List.of("/callback", "/callback")));
    }

    @Test
    void theSourceIsNamedInTheFailureSoItCanBeFound() {
        UrlPatternRules.IllegalPatternException e = assertThrows(
                UrlPatternRules.IllegalPatternException.class,
                () -> UrlPatternRules.assertLegal("URLControl.getSecuredURLs()", List.of("/iiif*/")));
        assertTrue(e.getMessage().contains("URLControl.getSecuredURLs()"), e.getMessage());
        assertTrue(e.getMessage().contains("/iiif*/"), e.getMessage());
    }

    // -------------------------------------------------------------- coverage

    /**
     * The half legality cannot catch. {@code "/invalidateSession"} is a perfectly legal exact spec
     * that matches nothing under {@code /invalidateSession/*}, where the servlet actually lives.
     */
    @Test
    void anExactPatternDoesNotCoverAPrefixMappedServlet() {
        UrlPatternRules.IllegalPatternException e = assertThrows(
                UrlPatternRules.IllegalPatternException.class,
                () -> UrlPatternRules.assertCovers("test",
                        List.of("/invalidateSession"), List.of("/invalidateSession/*")));
        assertTrue(e.getMessage().contains("unguarded"), e.getMessage());
    }

    @Test
    void aPrefixPatternCoversItsServlet() {
        assertDoesNotThrow(() -> UrlPatternRules.assertCovers("test",
                List.of("/invalidateSession/*"), List.of("/invalidateSession/*")));
    }

    @Test
    void anExactPatternCoversAnExactlyMappedServlet() {
        assertDoesNotThrow(() -> UrlPatternRules.assertCovers("test",
                List.of("/colorclasses"), List.of("/colorclasses")));
    }

    /**
     * The check only fires where the configuration already claimed the segment. Plenty of servlets
     * are anonymous by design, and demanding a guard for every one of them would be wrong.
     */
    @Test
    void aServletNoPatternClaimsIsLeftAlone() {
        assertDoesNotThrow(() -> UrlPatternRules.assertCovers("test",
                List.of("/callback"), List.of("/webid-login", "/rdf2", "/iiif/*")));
    }

    /** The original defect, as one assertion: the pattern named /iiif and did not cover it. */
    @Test
    void theIiifPatternDidNotCoverTheIiifServlet() {
        assertThrows(UrlPatternRules.IllegalPatternException.class,
                () -> UrlPatternRules.assertCovers("test",
                        List.of("/iiif/x"), List.of("/iiif/*")));
        assertDoesNotThrow(() -> UrlPatternRules.assertCovers("test",
                List.of("/iiif/*"), List.of("/iiif/*")));
    }
}
