package com.ebremer.lws.store.naming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Slug sanitisation — {@link Slugs}. The security-critical invariants come from C2: a slug must not
 * be able to forge a URI that escapes its container ({@code /}, {@code ..}) or impersonate an
 * auxiliary resource (a name ending in {@code .meta} or {@code .acr}, which {@code Target.resolve}
 * would read as some other resource's linkset or ACR — a resource that could then never be reached).
 */
class SlugsTest {

    @Test
    void plainNameSurvivesUnchanged() {
        assertEquals("report", Slugs.sanitize("report"));
        assertEquals("TCGA-AA-3872.svs", Slugs.sanitize("TCGA-AA-3872.svs"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "...", "///", "..", "  ..  "})
    void nothingUsableBecomesNull(String slug) {
        assertNull(Slugs.sanitize(slug), "an unusable slug is null, not an empty segment");
    }

    @Test
    void nullBecomesNull() {
        assertNull(Slugs.sanitize(null));
    }

    // --- C2: cannot escape the container ------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"../secret", "a/b/c", "..\\evil", "foo/../bar", "/etc/passwd"})
    void cannotEscapeTheContainer(String slug) {
        String s = Slugs.sanitize(slug);
        if (s != null) {
            assertFalse(s.contains("/"), () -> "a slash survived: " + s);
            assertFalse(s.contains("\\"), () -> "a backslash survived: " + s);
            assertFalse(s.contains(".."), () -> "a parent segment survived: " + s);
        }
    }

    // --- C2: cannot impersonate an auxiliary resource -----------------------

    @ParameterizedTest
    @ValueSource(strings = {"report.meta", "x.acr", "notes.meta", "a.b.meta", "deep.name.acr",
            "REPORT.META", "x.Acr"})
    void cannotEndInAnAuxiliarySuffix(String slug) {
        String s = Slugs.sanitize(slug);
        assertNotNull(s);
        String lower = s.toLowerCase(Locale.ROOT);
        assertFalse(lower.endsWith(".meta"), () -> "ends in .meta: " + s);
        assertFalse(lower.endsWith(".acr"), () -> "ends in .acr: " + s);
    }

    @Test
    void auxSuffixIsNeutralisedByAppending() {
        // Appending, not prefixing: "_report.meta" would still end in .meta. And the original stem
        // is preserved so a client can still recognise the name it asked for.
        assertEquals("report.meta_", Slugs.sanitize("report.meta"));
        assertEquals("x.acr_", Slugs.sanitize("x.acr"));
    }

    // --- C2: Windows device names (whole-name collisions) -------------------

    @ParameterizedTest
    @ValueSource(strings = {"con", "CON", "prn", "aux", "nul", "com1", "lpt9", "con.txt", "AUX.dat"})
    void reservedDeviceNamesArePrefixed(String slug) {
        String s = Slugs.sanitize(slug);
        assertNotNull(s);
        String stem = s.contains(".") ? s.substring(0, s.indexOf('.')) : s;
        assertFalse(RESERVED.contains(stem.toLowerCase(Locale.ROOT)),
                () -> "a reserved device name survived as the stem: " + s);
    }

    @Test
    void ordinaryNamesResemblingDevicesAreLeftAlone() {
        assertEquals("console", Slugs.sanitize("console"));
        assertEquals("printer", Slugs.sanitize("printer"));
    }

    // --- other cleanups -----------------------------------------------------

    @Test
    void leadingDotsAndTrailingWhitespaceAreStripped() {
        assertEquals("hidden", Slugs.sanitize(".hidden"));
        assertEquals("name", Slugs.sanitize("name...   "));
    }

    @Test
    void isTruncatedToACap() {
        String s = Slugs.sanitize("a".repeat(500));
        assertNotNull(s);
        assertTrue(s.length() <= 128, () -> "not truncated: " + s.length());
    }

    // --- extension derivation (M2) ------------------------------------------

    @Test
    void extensionOfNames() {
        assertEquals(".svs", Slugs.extensionOf("photo.svs"));
        assertEquals(".gz", Slugs.extensionOf("archive.tar.gz"), "the last segment is the extension");
        assertEquals(".svs", Slugs.extensionOf("PHOTO.SVS"), "lower-cased");
        assertEquals("", Slugs.extensionOf("noextension"));
        assertEquals("", Slugs.extensionOf("trailingdot."));
        assertEquals("", Slugs.extensionOf(null));
    }

    private static final java.util.Set<String> RESERVED = java.util.Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");
}
