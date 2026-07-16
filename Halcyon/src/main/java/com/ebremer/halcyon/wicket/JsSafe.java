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
     */
    public static String jsString(String value) {
        return harden(Json.createValue(value == null ? "" : value).toString());
    }

    /**
     * Harden an already-serialized JSON/JS payload so it cannot terminate the
     * enclosing {@code <script>} element. Only for text that is already valid
     * JSON/JS — it escapes the HTML-significant characters wherever they appear,
     * which is safe because in such a payload they only occur inside string
     * literals.
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
