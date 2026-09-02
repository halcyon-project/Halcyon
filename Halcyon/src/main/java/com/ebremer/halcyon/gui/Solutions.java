package com.ebremer.halcyon.gui;

import com.ebremer.vandegraph.Solution;
import org.apache.jena.graph.Node;

/**
 * Small read helpers for {@link Solution} bindings.
 *
 * @author erich
 */
final class Solutions {

    private Solutions() {
    }

    /**
     * An integer binding — typically a SPARQL {@code count()} — as an {@code int}.
     * <p>
     * M14: the three call sites used {@code (int) s.get(var).getLiteralValue()}.
     * That is an unchecked unbox of an {@code Object}, and it only works because of
     * an implementation detail of Jena: {@code XSDBaseNumericType.suitableInteger()}
     * narrows an {@code xsd:integer} to the smallest box that fits, so a small
     * {@code count()} arrives as an {@code Integer} and the cast succeeds. Verified:
     * the cast holds to {@code Integer.MAX_VALUE}, then yields {@code Long}
     * (ClassCastException) past it and {@code BigInteger} past {@code Long.MAX_VALUE}.
     * <p>
     * So the reported ClassCastException does not actually happen here — it needs a
     * count above 2^31, i.e. two billion ACL rules for one agent. The cast is still
     * worth removing: it silently depends on undocumented narrowing, and if that ever
     * changed, all six sites would break at once with an opaque message. Reading
     * through {@link Number} is correct for every box the narrowing can produce.
     *
     * @return the value, or 0 when the variable is unbound or not a numeric literal
     */
    static int intOf(Solution s, String var) {
        Node n = (s == null) ? null : s.get(var);
        if (n == null || !n.isLiteral()) {
            return 0;
        }
        Object v = n.getLiteralValue();
        return (v instanceof Number num) ? num.intValue() : 0;
    }
}
