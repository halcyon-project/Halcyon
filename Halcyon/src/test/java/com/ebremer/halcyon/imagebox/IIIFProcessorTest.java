package com.ebremer.halcyon.imagebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L9 — the IIIF request grammar.
 * <p>
 * Every malformed case below used to reach {@code ImageServer}'s catch-all as an
 * unchecked exception and come back as a <b>500 with a logged stack trace</b>, so
 * an unauthenticated one-liner could inflate the error rate and the log volume.
 * They are client errors and are now 400s.
 *
 * @author erich
 */
class IIIFProcessorTest {

    private static final String BASE = "https://h.example/img.svs";

    private static IIIFProcessor parse(String tail) throws Exception {
        return new IIIFProcessor(BASE + tail);
    }

    // ---- the shapes that must keep working -----------------------------------

    @Test
    @DisplayName("an ordinary tile request still parses")
    void ordinaryTile() throws Exception {
        IIIFProcessor i = parse("/0,0,512,512/512,512/0/default.jpg");
        assertTrue(i.tilerequest);
        assertEquals(0, i.x);
        assertEquals(512, i.w);
        assertEquals(512, i.tx);
        assertEquals(512, i.ty);
        assertEquals(0, i.rotation);
    }

    @Test
    @DisplayName("the one-sided size form '512,' is legal and leaves the other side 0")
    void oneSidedSize() throws Exception {
        // Java's split drops trailing empties, so this is ["512"] — length 1, which
        // is exactly why the sizes[1] guard exists. It must NOT be rejected.
        IIIFProcessor i = parse("/0,0,512,512/512,/0/default.jpg");
        assertEquals(512, i.tx);
        assertEquals(0, i.ty);
    }

    @Test
    @DisplayName("best-fit '!w,h' and '!w' parse")
    void bestFit() throws Exception {
        IIIFProcessor a = parse("/full/!360,270/0/default.jpg");
        assertTrue(a.aspectratio);
        assertEquals(360, a.tx);
        assertEquals(270, a.ty);
        IIIFProcessor b = parse("/full/!360/0/default.jpg");
        assertEquals(360, b.tx);
        assertEquals(360, b.ty, "!w with no h squares off the box");
    }

    @Test
    @DisplayName("full-region requests parse")
    void fullRegion() throws Exception {
        assertTrue(parse("/full/512,512/0/default.jpg").fullrequest);
    }

    // ---- the crashes ----------------------------------------------------------

    @Test
    @DisplayName("a bare ',' is a 400, not an AIOOBE")
    void bareComma() {
        // The trap: ",".split(",") is a ZERO-length array, not [""], so sizes[0]
        // threw ArrayIndexOutOfBounds.
        assertThrows(BadIIIFRequestException.class, () -> parse("/full/,/0/default.jpg"));
        assertThrows(BadIIIFRequestException.class, () -> parse("/full/,,/0/default.jpg"));
    }

    @Test
    @DisplayName("a leading empty size is a 400, not an NFE")
    void leadingEmpty() {
        assertThrows(BadIIIFRequestException.class, () -> parse("/full/,512/0/default.jpg"));
    }

    @Test
    @DisplayName("a bare '!' is a 400, not an NFE")
    void bareBang() {
        assertThrows(BadIIIFRequestException.class, () -> parse("/full/!/0/default.jpg"));
        assertThrows(BadIIIFRequestException.class, () -> parse("/full/!,512/0/default.jpg"));
    }

    @Test
    @DisplayName("an out-of-int-range number is a 400, not an NFE")
    void overflow() {
        // (\d+) is unbounded, so a long run of digits overflows Integer.parseInt.
        assertThrows(BadIIIFRequestException.class,
                () -> parse("/99999999999,0,1,1/512,/0/default.jpg"));
        assertThrows(BadIIIFRequestException.class,
                () -> parse("/full/99999999999,/0/default.jpg"));
    }

    // ---- rotation -------------------------------------------------------------

    @Test
    @DisplayName("rotation 0 is accepted")
    void rotationZero() throws Exception {
        assertEquals(0, parse("/full/512,512/0/default.jpg").rotation);
    }

    @Test
    @DisplayName("a rotation we do not implement is refused, not silently ignored")
    void rotationRefused() {
        // The actual L9 finding: `rotation` was parsed and never read again, so these
        // returned an UNROTATED image with HTTP 200 — wrong pixels under a success
        // code, which no client can detect.
        for (String r : new String[] {"90", "180", "270", "45", "7", "359"}) {
            assertThrows(BadIIIFRequestException.class,
                    () -> parse("/full/512,512/" + r + "/default.jpg"),
                    "rotation " + r + " was accepted and would be ignored");
        }
    }
}
