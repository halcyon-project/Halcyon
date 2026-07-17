package com.ebremer.halcyon.imagebox;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * C3 — the allocation budget that bounds a IIIF size request.
 * <p>
 * Promoted from a throwaway harness (F1). C3's history is the argument for keeping
 * these: the finding was reported as fixed once already, and the fix was bypassable —
 * a one-sided size ("512,") has a zero product, so a check on the REQUESTED size said
 * nothing about the real allocation.
 *
 * @author erich
 */
class OutputBudgetTest {

    @Test
    @DisplayName("ordinary tile sizes are allowed")
    void ordinarySizes() {
        assertTrue(ImageServer.withinOutputBudget(256, 256));
        assertTrue(ImageServer.withinOutputBudget(1600, 800));
    }

    @Test
    @DisplayName("a zero dimension is refused — it never resolved to a rectangle")
    void zeroIsRefused() {
        // The C3 bypass: /full/0,/0/default.jpg had product 0, slipped the budget,
        // and normalizePreferredSize then expanded it back to the FULL image.
        assertFalse(ImageServer.withinOutputBudget(0, 0));
        assertFalse(ImageServer.withinOutputBudget(512, 0));
        assertFalse(ImageServer.withinOutputBudget(0, 512));
    }

    @Test
    @DisplayName("negative dimensions are refused")
    void negativeIsRefused() {
        assertFalse(ImageServer.withinOutputBudget(-1, 256));
        assertFalse(ImageServer.withinOutputBudget(256, -1));
    }

    @Test
    @DisplayName("per-dimension cap holds")
    void perDimensionCap() {
        assertTrue(ImageServer.withinOutputBudget(20000, 1));
        assertFalse(ImageServer.withinOutputBudget(20001, 1));
    }

    @Test
    @DisplayName("total-pixel cap holds independently of each dimension")
    void totalPixelCap() {
        // Both sides individually legal, product not.
        assertFalse(ImageServer.withinOutputBudget(20000, 20000));
    }

    @Test
    @DisplayName("a huge size cannot overflow int and flip negative")
    void noIntOverflow() {
        // (long) math on the product is what stops MAX_VALUE*2 wrapping positive-small.
        assertFalse(ImageServer.withinOutputBudget(Integer.MAX_VALUE, 2));
        assertFalse(ImageServer.withinOutputBudget(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
