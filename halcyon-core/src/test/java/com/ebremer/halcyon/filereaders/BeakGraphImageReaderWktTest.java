package com.ebremer.halcyon.filereaders;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The WKT that BeakGraphImageReader rasterizes and rescales. The stored
 * per-series WKT is meant to be on the integer grid, but BeakGraph's repair of
 * an invalid polygon (JTS buffer(0)) adds fractional vertices. The old
 * integer-only pattern {@code (\d+)\s+(\d+)} read the digits after the point:
 * a long fraction overflowed parseInt, which dropped the rest of that grid
 * cell's polygons, and a short one gave a wrong vertex. The three geometries
 * below are the ones readTile logged on 2026-11-16 (TCGA COAD feature files).
 *
 * @author erich
 */
class BeakGraphImageReaderWktTest {

    private static final String LOGGED_1 = "POLYGON ((5838.666666666667 3535.5, 5842 3533, 5843 3532, 5843 3531, 5842 3531, 5841 3532, 5841 3533, 5840 3533, 5840 3534, 5839 3535, 5838.666666666667 3535.5))";
    private static final String LOGGED_2 = "POLYGON ((21084 4608, 21084 4612, 21085 4612, 21085 4613, 21084 4614, 21084 4618, 21085 4618, 21088 4621, 21090 4621, 21088 4619, 21087 4619, 21087 4618, 21088 4618, 21089 4617, 21090 4617, 21090 4616, 21089 4615, 21089 4614, 21090 4614, 21090 4613, 21092 4613, 21092 4612, 21092.666666666668 4611.333333333333, 21092 4611, 21091 4610, 21091 4609, 21090 4609, 21089 4608, 21084 4608))";
    private static final String LOGGED_3 = "POLYGON ((10051 15693, 10050 15693, 10050 15695, 10051.333333333334 15695.666666666666, 10052 15695, 10052 15694, 10052 15693, 10051 15693))";

    /** The parser before this fix. */
    private static Polygon legacyParse(String wkt, int offsetX, int offsetY) {
        Matcher matcher = Pattern.compile("(\\d+)\\s+(\\d+)").matcher(wkt);
        List<Integer> xs = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();
        while (matcher.find()) {
            xs.add(Integer.parseInt(matcher.group(1)) - offsetX);
            ys.add(Integer.parseInt(matcher.group(2)) - offsetY);
        }
        return new Polygon(xs.stream().mapToInt(i -> i).toArray(), ys.stream().mapToInt(i -> i).toArray(), xs.size());
    }

    /** Rasterize the way readTile does: ARGB, red, BasicStroke(1), fillPolygon. */
    private static int[] render(Polygon polygon, int w, int h) {
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        g2d.setColor(Color.RED);
        g2d.fillPolygon(polygon);
        g2d.dispose();
        return bi.getRGB(0, 0, w, h, null, 0, w);
    }

    @Test
    @DisplayName("the logged geometries broke the old parser")
    void legacyFails() {
        assertThrows(NumberFormatException.class, () -> legacyParse(LOGGED_1, 0, 0));
        assertThrows(NumberFormatException.class, () -> legacyParse(LOGGED_2, 0, 0));
        assertThrows(NumberFormatException.class, () -> legacyParse(LOGGED_3, 0, 0));
    }

    @Test
    @DisplayName("the logged geometries parse, fractional vertices rounded to the nearest pixel")
    void loggedGeometriesParse() {
        Polygon p1 = BeakGraphImageReader.parseWktToPolygon(LOGGED_1, 0, 0);
        assertEquals(11, p1.npoints);
        assertEquals(5839, p1.xpoints[0]);
        assertEquals(3536, p1.ypoints[0]);
        assertEquals(new Rectangle(5839, 3531, 4, 5), p1.getBounds());

        Polygon p2 = BeakGraphImageReader.parseWktToPolygon(LOGGED_2, 0, 0);
        assertEquals(29, p2.npoints);
        assertEquals(21093, p2.xpoints[22]);
        assertEquals(4611, p2.ypoints[22]);
        assertEquals(new Rectangle(21084, 4608, 9, 13), p2.getBounds());

        Polygon p3 = BeakGraphImageReader.parseWktToPolygon(LOGGED_3, 0, 0);
        assertEquals(8, p3.npoints);
        assertEquals(10051, p3.xpoints[3]);
        assertEquals(15696, p3.ypoints[3]);
        assertEquals(new Rectangle(10050, 15693, 2, 3), p3.getBounds());
    }

    @Test
    @DisplayName("a logged geometry rasterizes in a tile at its own offset")
    void loggedGeometryRenders() {
        int[] px = render(BeakGraphImageReader.parseWktToPolygon(LOGGED_2, 21080, 4600), 32, 32);
        assertNotEquals(0, px[10 * 32 + 6]);   // (21086, 4610): inside
        assertEquals(0, px[1 * 32 + 1]);       // (21081, 4601): outside
    }

    @Test
    @DisplayName("a short fraction no longer becomes a stray vertex")
    void shortFraction() {
        // The old pattern read "5 200" out of "100.5 200": a vertex at x = 5.
        String wkt = "POLYGON ((100.5 200, 110 200, 110 210, 100 210, 100.5 200))";
        assertEquals(5, legacyParse(wkt, 0, 0).getBounds().x);
        assertEquals(100, BeakGraphImageReader.parseWktToPolygon(wkt, 0, 0).getBounds().x);
    }

    @Test
    @DisplayName("a negative coordinate keeps its sign")
    void negative() {
        Polygon p = BeakGraphImageReader.parseWktToPolygon("POLYGON ((-5 -7, 10 -7, 10 3, -5 -7))", 0, 0);
        assertEquals(new Rectangle(-5, -7, 15, 10), p.getBounds());
    }

    @Test
    @DisplayName("integer geometries give the same polygon and pixels as before")
    void integerDataUnchanged() {
        Random random = new Random(20261116L);
        for (int n = 0; n < 500; n++) {
            int vertices = 3 + random.nextInt(30);
            int ox = 20 + random.nextInt(50000);
            int oy = 20 + random.nextInt(50000);
            StringBuilder sb = new StringBuilder("POLYGON ((");
            int fx = 0, fy = 0;
            for (int v = 0; v < vertices; v++) {
                int x = ox - 20 + random.nextInt(300);
                int y = oy - 20 + random.nextInt(300);
                if (v == 0) { fx = x; fy = y; }
                sb.append(x).append(' ').append(y).append(", ");
            }
            sb.append(fx).append(' ').append(fy).append("))");
            String wkt = sb.toString();
            Polygon before = legacyParse(wkt, ox, oy);
            Polygon after = BeakGraphImageReader.parseWktToPolygon(wkt, ox, oy);
            assertEquals(before.npoints, after.npoints, wkt);
            assertArrayEquals(before.xpoints, after.xpoints, wkt);
            assertArrayEquals(before.ypoints, after.ypoints, wkt);
            assertArrayEquals(render(before, 256, 256), render(after, 256, 256), wkt);
        }
    }

    @Test
    @DisplayName("a hole stays empty and multipolygon parts are not joined")
    void rings() {
        int[] holed = render(BeakGraphImageReader.parseWktToPolygon(
            "POLYGON ((0 0, 20 0, 20 20, 0 20, 0 0), (5 5, 15 5, 15 15, 5 15, 5 5))", 0, 0), 20, 20);
        assertNotEquals(0, holed[2 * 20 + 2]);
        assertEquals(0, holed[10 * 20 + 10]);

        int[] two = render(BeakGraphImageReader.parseWktToPolygon(
            "MULTIPOLYGON (((0 0, 5 0, 5 5, 0 5, 0 0)), ((15 0, 20 0, 20 5, 15 5, 15 0)))", 0, 0), 20, 20);
        assertNotEquals(0, two[2 * 20 + 2]);
        assertNotEquals(0, two[2 * 20 + 17]);
        assertEquals(0, two[2 * 20 + 10]);
    }

    @Test
    @DisplayName("rescaling keeps integers integral and fractional vertices exact")
    void rescale() {
        assertEquals(LOGGED_1, BeakGraphImageReader.rescaleWKT(LOGGED_1, 1));
        assertEquals("POLYGON ((10 20, 30 40, 10 20))", BeakGraphImageReader.rescaleWKT("POLYGON ((5 10, 15 20, 5 10))", 2));
        assertEquals("POLYGON ((11677.333333333334 7071.0, 11684 7066))",
            BeakGraphImageReader.rescaleWKT("POLYGON ((5838.666666666667 3535.5, 5842 3533))", 2));
        // No pair spans a CRS IRI prefix.
        assertEquals("<http://www.opengis.net/def/crs/OGC/1.3/CRS84> POLYGON ((40 80, 4 8))",
            BeakGraphImageReader.rescaleWKT("<http://www.opengis.net/def/crs/OGC/1.3/CRS84> POLYGON ((10 20, 1 2))", 4));
    }
}
