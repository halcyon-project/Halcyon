package com.ebremer.halcyon.geometry;

/**
 *
 * @author erich
 */
public class Vector2D {
    int a;
    int b;
    
    Vector2D(int x, int y) {
        a = x;
        b = y;
    }
    
    public double Magnitude() {
        // (long) products: a and b are ints, so a*a was evaluated in int and
        // wrapped for |a| > 46340 — the widening to double happened only AFTER
        // the overflow, which is exactly late enough to be useless.
        return Math.sqrt((double) ((long) a * a + (long) b * b));
    }

    public static double Magnitude(long[] a) {
        long sum = 0;
        for (int c=0; c<a.length; c++) {
            sum = sum + (a[c]*a[c]);
        }
        return Math.sqrt(sum);
    }

    public static double Magnitude(int[] a) {
        long sum = 0;
        for (int c=0; c<a.length; c++) {
            // (long) cast on the FIRST operand: the sum was already long, but
            // a[c]*a[c] is an int expression and overflowed before it ever got
            // widened, so a large component could contribute a NEGATIVE square.
            sum = sum + ((long) a[c] * a[c]);
        }
        return Math.sqrt(sum);
    }

    public static double Magnitude(Point a) {
        // Was (a.x*a.x)+(a.y+a.y) — i.e. x² + 2y. Not a rounding quirk: for
        // (3,4) it returned sqrt(17)=4.12 instead of 5, and for any y<0 large
        // enough it went imaginary and returned NaN. SmallestMag() is built on
        // this, so it was ordering by a quantity that is not a magnitude.
        return Math.sqrt((double) ((long) a.x * a.x + (long) a.y * a.y));
    }

    public static Point SmallestMag(Point a, Point b) {
        if (Magnitude(a)<Magnitude(b)) {
            return a;
        } else {
            return b;
        }
    }
}
