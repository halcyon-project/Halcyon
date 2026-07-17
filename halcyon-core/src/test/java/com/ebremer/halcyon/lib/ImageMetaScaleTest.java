package com.ebremer.halcyon.lib;

import com.ebremer.halcyon.lib.ImageMeta.ImageScale;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * H12 — pyramid level selection.
 * <p>
 * Promoted from a throwaway harness (F1). Two distinct bugs live here, and only one
 * of them was loud: an empty scale list walked off the front of the list
 * ({@code scales.get(-1)}) so every tile of a non-pyramidal image 500'd — but the
 * quiet one mattered more, because a pyramid whose base was never seeded silently
 * served HALF RESOLUTION for a 1:1 request and nobody would notice.
 *
 * @author erich
 */
class ImageMetaScaleTest {

    private static ImageMeta pyramid() {
        // What the readers build now: base (scale 1) plus the reduced levels.
        return ImageMeta.Builder.getBuilder(0, 64, 48)
                .addScale(0, 64, 48)
                .addScale(1, 32, 24)
                .addScale(2, 16, 12)
                .addScale(3, 8, 6)
                .build();
    }

    @Test
    @DisplayName("an empty scale list falls back to the base rather than throwing")
    void emptyScalesFallsBackToBase() {
        ImageMeta empty = ImageMeta.Builder.getBuilder(0, 64, 48)
                .setTileSizeX(64).setTileSizeY(48).build();
        ImageScale s = empty.getBestMatch(1.0);
        assertNotNull(s, "must not return null");
        assertEquals(1, s.scale());
        assertEquals(64, s.width());
        assertEquals(48, s.height());
    }

    @Test
    @DisplayName("a 1:1 request selects FULL resolution, not the first reduced level")
    void oneToOneSelectsFullResolution() {
        // The silent half: with the base missing, this returned scale=2 (32x24) for a
        // full-resolution request — a wrong image, with no error.
        ImageScale s = pyramid().getBestMatch(1.0);
        assertEquals(1, s.scale());
        assertEquals(0, s.series());
    }

    @Test
    @DisplayName("a reduced request selects the matching level")
    void reducedSelectsMatchingLevel() {
        assertEquals(4, pyramid().getBestMatch(4.0).scale());
        assertEquals(2, pyramid().getBestMatch(2.0).scale());
    }

    @Test
    @DisplayName("a ratio beyond the pyramid clamps to the smallest level")
    void hugeRatioClampsToSmallest() {
        assertEquals(8, pyramid().getBestMatch(99.0).scale());
    }

    @Test
    @DisplayName("a sub-1 ratio floors at full resolution")
    void subOneRatioFloorsAtFullResolution() {
        assertEquals(1, pyramid().getBestMatch(0.5).scale());
    }
}
