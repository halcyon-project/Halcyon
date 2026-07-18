package com.ebremer.halcyon.wicket;

import jakarta.json.Json;

/**
 * Safe emission of server-side values into inline {@code <script>} blocks (C5).
 * <p>
 * Wicket's {@code JavaScriptHeaderItem.forScript(...)} renders its argument
 * <em>inside</em> an inline {@code <script>} element, so a value pasted into it
 * can escape in two different ways:
 * <ul>
 *   <li><b>out of the JS literal</b> — a quote (for {@code '...'}), or a backtick
 *       or {@code ${} (for a template literal), ends the literal and the rest is
 *       executed as code;</li>
 *   <li><b>out of the {@code <script>} element</b> — the HTML parser ends the
 *       block at the first {@code </script>} regardless of JS quoting, so JSON
 *       encoding alone (which never escapes {@code <} or {@code /}) is NOT
 *       enough.</li>
 * </ul>
 * Both are closed here: values are JSON-encoded (quotes, backslashes, control
 * characters, newlines) and then {@code <}, {@code >} and {@code &} are
 * additionally emitted as {@code \\uXXXX}, which is inert to the HTML parser and
 * still decodes to the original character in JS. U+2028/U+2029 are escaped too —
 * JSON permits them raw, but they terminate a JS line.
 *
 * @author erich
 */
public final class JsSafe {

    /** U+2028 LINE SEPARATOR / U+2029 PARAGRAPH SEPARATOR, without embedding them. */
    private static final String LINE_SEP = String.valueOf((char) 0x2028);
    private static final String PARA_SEP = String.valueOf((char) 0x2029);

    private JsSafe() {}

    /**
     * A complete, quoted JS string literal for {@code value} that cannot break
     * out of either the literal or the enclosing {@code <script>} element. Use
     * this instead of concatenating a raw value between quotes.
     * <p>
     * The backtick and {@code ${} escaping is belt-and-braces: the returned
     * literal is double-quoted, so template-literal syntax inside it is already
     * inert, and no caller nests it in a template literal today. It is done
     * anyway because the one that DID — Zephyr pasting saved Turtle between
     * backticks — is the whole reason C5 exists, and the escape costs nothing:
     * every character of {@code value} is inside the quoted literal by
     * construction, and {@code \\u0060} / {@code \\u0024} decode back to the
     * originals. This is only safe here, not in {@link #inlineScriptPayload}.
     */
    public static String jsString(String value) {
        return harden(Json.createValue(value == null ? "" : value).toString())
                .replace("`", "\\u0060")
                .replace("${", "\\u0024{");
    }

    /**
     * Harden an already-serialized JSON/JS payload so it cannot terminate the
     * enclosing {@code <script>} element. Only for text that is already valid
     * JSON/JS — it escapes the HTML-significant characters wherever they appear,
     * which is safe because in such a payload they only occur inside string
     * literals.
     * <p>
     * Do NOT extend this with the backtick / {@code ${} escaping that
     * {@link #jsString} applies. The argument here is CODE, not a string literal
     * ({@code getMV()} is "var images = [...]"), so a {@code \\u0060} landing
     * outside a literal is a syntax error rather than an escape.
     */
    public static String inlineScriptPayload(String payload) {
        return payload == null ? "" : harden(payload);
    }

    private static String harden(String s) {
        return s
            .replace("<", "\\u003C")
            .replace(">", "\\u003E")
            .replace("&", "\\u0026")
            .replace(LINE_SEP, "\\u2028")
            .replace(PARA_SEP, "\\u2029");
    }
}
