package com.ebremer.halcyon.hilbert;

import java.awt.Polygon;
import java.util.LinkedList;
import java.util.TreeSet;
import com.ebremer.halcyon.geometry.Point;
import org.davidmoten.hilbert.Range;
import org.davidmoten.hilbert.Ranges;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D5 — the dead Hilbert-indexing island ({@code HilbertSpace}, {@code hTools},
 * {@code eRange}).
 * <p>
 * Nothing references {@code HilbertSpace} today, so none of this is on a live
 * path; it is kept rather than deleted because it is research code that may be
 * revived, and the point of D5 is that reviving it must not quietly hand back
 * wrong tiles. {@code Range}/{@code Ranges} here are third-party
 * ({@code org.davidmoten.hilbert}), not ours.
 *
 * @author erich
 */
class HilbertAlgorithmTest {

    private static Ranges ranges(long low, long high) {
        Ranges r = new Ranges(4);
        r.add(low, high);
        return r;
    }

    // ---- eRange.compareTo ---------------------------------------------------

    @Test
    @DisplayName("compareTo is consistent with equals — it compared only low")
    void compareToConsistentWithEquals() {
        eRange a = new eRange(5, 7);
        eRange b = new eRange(5, 9);
        // The bug: same low, different high => compareTo said 0 while equals says
        // not-equal, which violates Comparable's contract.
        assertFalse(a.equals(b), "precondition: these are different ranges");
        assertTrue(a.compareTo(b) != 0, "compareTo reported 0 for unequal ranges");
        // and the ordering is still the intended one
        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
        assertEquals(0, a.compareTo(new eRange(5, 7)), "equal ranges must compare 0");
    }

    @Test
    @DisplayName("a TreeSet keeps both ranges rather than swallowing one")
    void treeSetDoesNotDedupeDistinctRanges() {
        // Why the contract violation mattered: a sorted set deduplicates on
        // compareTo==0, so [5,9] silently vanished the moment [5,7] was present.
        TreeSet<eRange> set = new TreeSet<>();
        set.add(new eRange(5, 7));
        set.add(new eRange(5, 9));
        assertEquals(2, set.size(), "a distinct range was swallowed by the sorted set");
    }

    // ---- HilbertSpace.inRange(Ranges, ...) ----------------------------------

    @Test
    @DisplayName("inRange(Ranges,...) can return true — the arrow switch discarded it")
    void inRangeRangesReturnsResult() {
        HilbertSpace hs = new HilbertSpace();
        Point p = new Point(10, 10);
        // The neighbour to the East of (10,10) is (11,10); build a Ranges that
        // contains exactly that cell's index.
        long east = hs.hc.index(new long[] {11, 10});
        Ranges rr = ranges(east, east);
        // Before the fix this was false for every input, because `case E ->
        // contains(...)` in a switch STATEMENT throws the value away.
        assertTrue(hs.inRange(rr, p, HilbertSpace.E), "inRange(Ranges,...) never returned true");
    }

    @Test
    @DisplayName("inRange(Ranges,...) agrees with the LinkedList overload")
    void inRangeOverloadsAgree() {
        // The LinkedList overload used `case E: return contains(...)` and was
        // always correct — it is the oracle for the one that was not.
        HilbertSpace hs = new HilbertSpace();
        Point p = new Point(10, 10);
        long east = hs.hc.index(new long[] {11, 10});

        LinkedList<Range> list = new LinkedList<>();
        list.add(new Range(east, east));
        Ranges rr = ranges(east, east);

        for (byte n : new byte[] {HilbertSpace.N, HilbertSpace.NE, HilbertSpace.E, HilbertSpace.SE,
                                  HilbertSpace.S, HilbertSpace.SW, HilbertSpace.W, HilbertSpace.NW}) {
            assertEquals(hs.inRange(list, p, n), hs.inRange(rr, p, n),
                    "overloads disagree for neighbour " + n);
        }
    }

    @Test
    @DisplayName("inRange(Ranges,...) is still false when the neighbour is absent")
    void inRangeFalseWhenAbsent() {
        HilbertSpace hs = new HilbertSpace();
        Point p = new Point(10, 10);
        long far = hs.hc.index(new long[] {5000, 5000});
        assertFalse(hs.inRange(ranges(far, far), p, HilbertSpace.E));
    }

    // ---- HilbertSpace.Fatten -------------------------------------------------

    @Test
    @DisplayName("Fatten emits every run, not just one")
    void fattenEmitsAllRuns() {
        // The bug: the inner while had no break, so it drained the iterator and
        // exactly ONE Range came out no matter how many disjoint runs existed.
        // Two well-separated cells fatten into two disjoint clusters.
        HilbertSpace hs = new HilbertSpace();
        Ranges in = new Ranges(4);
        long a = hs.hc.index(new long[] {1, 1});
        long b = hs.hc.index(new long[] {900, 900});
        in.add(Math.min(a, b), Math.min(a, b));
        in.add(Math.max(a, b), Math.max(a, b));

        int count = 0;
        long prevHigh = Long.MIN_VALUE;
        for (Range r : hs.Fatten(in)) {
            count++;
            assertTrue(r.low() <= r.high(), "range is inverted: " + r);
            assertTrue(r.low() > prevHigh, "ranges are not sorted/disjoint: " + r);
            prevHigh = r.high();
        }
        assertTrue(count > 1, "Fatten collapsed everything into " + count + " range(s)");
    }

    @Test
    @DisplayName("Fatten does not bridge a gap between distant cells")
    void fattenDoesNotBridgeGaps() {
        // The old loop kept extending `last` whenever some far-away later value
        // happened to sit at last+1, so one range could span the whole space.
        HilbertSpace hs = new HilbertSpace();
        long a = hs.hc.index(new long[] {1, 1});
        long b = hs.hc.index(new long[] {900, 900});
        Ranges in = new Ranges(4);
        in.add(Math.min(a, b), Math.min(a, b));
        in.add(Math.max(a, b), Math.max(a, b));

        long covered = 0;
        for (Range r : hs.Fatten(in)) {
            covered += (r.high() - r.low() + 1);
        }
        // Each seed contributes itself plus its two neighbours, so the covered
        // cell count is small and bounded — nothing like the span between them.
        assertTrue(covered <= 8, "Fatten covered " + covered + " cells; it bridged the gap");
        assertTrue(Math.abs(a - b) > 100, "precondition: the two cells are far apart");
    }

    @Test
    @DisplayName("Fatten of an empty input is empty")
    void fattenEmpty() {
        HilbertSpace hs = new HilbertSpace();
        assertFalse(hs.Fatten(new Ranges(4)).iterator().hasNext());
    }

    // ---- HilbertSpace.Polygon2Hilbert ---------------------------------------

    @Test
    @DisplayName("a polygon enclosing no lattice point yields NO range, not Range(0,0)")
    void polygonWithNoLatticePointIsEmpty() {
        // The bug: sv/ev stayed at their 0 initialisers and the unconditional
        // add() emitted Range(0,0) — Hilbert index 0 is a real cell at the
        // origin, so an empty selection claimed a tile at the slide corner.
        HilbertSpace hs = new HilbertSpace();
        // A degenerate polygon: zero area, contains() is false everywhere.
        Polygon degenerate = new Polygon(new int[] {50, 50, 50}, new int[] {50, 50, 50}, 3);
        LinkedList<Range> out = hs.Polygon2Hilbert(degenerate);
        assertTrue(out.isEmpty(), "expected no ranges, got " + out);
    }

    @Test
    @DisplayName("an ordinary polygon still produces its ranges")
    void polygonStillWorks() {
        // The guard must not have turned the normal path into a no-op.
        HilbertSpace hs = new HilbertSpace();
        Polygon square = new Polygon(new int[] {10, 30, 30, 10}, new int[] {10, 10, 30, 30}, 4);
        LinkedList<Range> out = hs.Polygon2Hilbert(square);
        assertFalse(out.isEmpty(), "a 20x20 square produced no ranges");
        for (Range r : out) {
            assertTrue(r.low() <= r.high(), "inverted range: " + r);
            assertTrue(r.low() > 0, "phantom origin range leaked in: " + r);
        }
    }
}
