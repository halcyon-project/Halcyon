package com.ebremer.halcyon.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D5 — {@code Vector2D.Magnitude}.
 * <p>
 * Dead today (only {@code HilbertSpace} refers to Vector2D, and nothing refers
 * to HilbertSpace), so these exist to stop the math being silently wrong if it
 * is ever revived rather than to guard a live path.
 *
 * @author erich
 */
class Vector2DTest {

    private static final double EPS = 1e-9;

    @Test
    @DisplayName("Magnitude(Point) is sqrt(x²+y²) — it computed x²+2y")
    void pointMagnitudeUsesYSquared() {
        // The whole bug in one assertion: (3,4) is the textbook 3-4-5 triangle.
        // The old expression (a.x*a.x)+(a.y+a.y) gave sqrt(9+8)=sqrt(17)=4.123.
        assertEquals(5.0, Vector2D.Magnitude(new Point(3, 4)), EPS);
        assertEquals(13.0, Vector2D.Magnitude(new Point(5, 12)), EPS);
    }

    @Test
    @DisplayName("Magnitude(Point) handles negative y instead of going imaginary")
    void pointMagnitudeNegativeY() {
        // x²+2y goes NEGATIVE for y sufficiently negative, and sqrt of that is
        // NaN — so the old code returned NaN for ordinary points below the axis.
        // NaN compares false against everything, which is how SmallestMag could
        // silently pick the wrong point rather than throw.
        double m = Vector2D.Magnitude(new Point(3, -4));
        assertTrue(!Double.isNaN(m), "magnitude went NaN for a negative y");
        assertEquals(5.0, m, EPS);
    }

    @Test
    @DisplayName("magnitude is symmetric in sign — it was not")
    void signSymmetry() {
        assertEquals(Vector2D.Magnitude(new Point(3, 4)),
                     Vector2D.Magnitude(new Point(3, -4)), EPS);
        assertEquals(Vector2D.Magnitude(new Point(-3, 4)),
                     Vector2D.Magnitude(new Point(3, 4)), EPS);
    }

    @Test
    @DisplayName("SmallestMag orders by actual magnitude")
    void smallestMag() {
        // SmallestMag is built on Magnitude(Point), so it inherited the wrong
        // quantity. This pair is chosen because the old formula gets it
        // BACKWARDS rather than merely imprecise:
        //   (0,5) true 5.000, x²+2y -> sqrt(10) = 3.162
        //   (4,0) true 4.000, x²+2y -> sqrt(16) = 4.000
        // so the old code called (0,5) the smaller of the two, when it is the
        // larger. Most pairs agree by luck; this one cannot.
        Point tall = new Point(0, 5);
        Point wide = new Point(4, 0);
        assertEquals(wide, Vector2D.SmallestMag(tall, wide));
        assertEquals(wide, Vector2D.SmallestMag(wide, tall));
    }

    @Test
    @DisplayName("Magnitude(int[]) does not overflow int on a large component")
    void intArrayNoOverflow() {
        // 50000² = 2.5e9 > Integer.MAX_VALUE. The sum was long, but a[c]*a[c] is
        // an int expression, so it wrapped NEGATIVE before it was ever widened —
        // and sqrt of a negative sum is NaN. The magnitude of a single-component
        // vector is just that component.
        assertEquals(50000.0, Vector2D.Magnitude(new int[] {50000}), EPS);
        assertEquals(Math.sqrt(2.0) * 50000.0, Vector2D.Magnitude(new int[] {50000, 50000}), 1e-3);
    }

    @Test
    @DisplayName("the instance Magnitude() does not overflow int either")
    void instanceNoOverflow() {
        assertEquals(5.0, new Vector2D(3, 4).Magnitude(), EPS);
        // Same int-overflow shape as above, via (a*a)+(b*b).
        double m = new Vector2D(50000, 0).Magnitude();
        assertTrue(!Double.isNaN(m), "instance magnitude went NaN on overflow");
        assertEquals(50000.0, m, EPS);
    }

    @Test
    @DisplayName("Magnitude(long[]) — the one that was already right")
    void longArrayUnchanged() {
        assertEquals(5.0, Vector2D.Magnitude(new long[] {3, 4}), EPS);
    }
}
