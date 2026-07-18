package com.ebremer.halcyon.wicket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * C5 — the escaping that stops a stored/reflected value from breaking out of an
 * inline {@code <script>}.
 * <p>
 * Promoted from a throwaway harness (F1). These assertions are the reason the C5
 * chain is closed, so they are the ones most worth keeping: if {@code JsSafe} ever
 * regresses, the Zephyr stored-Turtle sink becomes account takeover again.
 *
 * @author erich
 */
class JsSafeTest {

    /** The critical property: JSON encoding alone does NOT escape '<'. */
    @Test
    @DisplayName("</script> cannot terminate the enclosing script block")
    void closesScriptTag() {
        String out = JsSafe.jsString("</script><script>alert(1)</script>");
        assertFalse(out.contains("</script>"), "raw </script> survived: " + out);
        assertTrue(out.contains("\\u003C"), "'<' must be unicode-escaped: " + out);
    }

    @Test
    @DisplayName("a backtick cannot break out of a template literal")
    void backtick() {
        // Defense in depth, not a live hole: the returned literal is double-quoted,
        // so a backtick in it is already inert, and no caller nests it in a template
        // literal today. It is asserted because the caller that DID — Zephyr pasting
        // saved Turtle between backticks, where a stored backtick escaped and ran for
        // whoever opened the stack — is the reason C5 exists. Cheap to keep inert.
        String out = JsSafe.jsString("`+alert(1)+`");
        assertFalse(out.contains("`"), "raw backtick survived: " + out);
    }

    @Test
    @DisplayName("${...} cannot interpolate")
    void templateInterpolation() {
        String out = JsSafe.jsString("${alert(1)}");
        // Same standing as backtick(): inert already by virtue of the double quotes,
        // escaped anyway so the value stays safe in EVERY JS string context.
        assertFalse(out.contains("${"), "raw ${ survived: " + out);
    }

    @Test
    @DisplayName("quotes cannot end the string")
    void quotes() {
        String out = JsSafe.jsString("\"';alert(1);//");
        assertTrue(out.startsWith("\"") && out.endsWith("\""), "must be a quoted JSON string: " + out);
        // the inner quote must be escaped, not bare
        assertFalse(out.substring(1, out.length() - 1).contains("\"")
                    && !out.substring(1, out.length() - 1).contains("\\\""),
                    "inner quote left unescaped: " + out);
    }

    @Test
    @DisplayName("U+2028/U+2029 are escaped (they are line terminators in JS)")
    void lineSeparators() {
        String out = JsSafe.jsString("a" + ((char) 0x2028) + "b" + ((char) 0x2029) + "c");
        assertFalse(out.indexOf(0x2028) >= 0, "raw U+2028 survived");
        assertFalse(out.indexOf(0x2029) >= 0, "raw U+2029 survived");
    }

    @Test
    @DisplayName("ordinary values still round-trip readably")
    void ordinaryValuesSurvive() {
        String out = JsSafe.jsString("https://localhost:8888/lws/utah/HnE/");
        assertTrue(out.contains("localhost:8888"), "a normal URL should pass through: " + out);
        assertTrue(out.startsWith("\"") && out.endsWith("\""));
    }

    @Test
    @DisplayName("null is a JS null, not the text \"null\" spliced into code")
    void nullValue() {
        String out = JsSafe.jsString(null);
        assertTrue("null".equals(out) || "\"\"".equals(out), "unexpected encoding of null: " + out);
    }
}
