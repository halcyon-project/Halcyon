package com.ebremer.halcyon.lws;

import com.ebremer.ns.HAL;
import com.ebremer.ns.VG;
import com.ebremer.ns.ZEPH;
import com.ebremer.vandegraph.media.MediaBindings;
import java.io.InputStream;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins Halcyon's media-binding overlay ({@code halcyon/media-bindings.ttl}):
 * Zephyr is the default viewer/editor for whole-slide TIFFs and zeph:Stack
 * resources, the plain image viewer survives as a listed alternate, and the
 * vandegraph defaults stay untouched for everything else.
 */
class HalcyonMediaBindingsTest {

    private static MediaBindings bindings() {
        Model overlay = ModelFactory.createDefaultModel();
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("halcyon/media-bindings.ttl")) {
            assertNotNull(in, "halcyon/media-bindings.ttl not on the classpath");
            RDFDataMgr.read(overlay, in, Lang.TURTLE);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return MediaBindings.parseWithDefaults(overlay);
    }

    @Test
    void wholeSlideTiffsOpenInZephyr() {
        MediaBindings.Resolved r = bindings().resolve("image/tiff", Set.of());
        assertEquals(HAL.ZephyrViewer.asNode(), r.viewer(),
                "exact image/tiff beats the defaults' image/* pattern");
        assertTrue(r.alternates().contains(VG.HtmlImageViewer.asNode()),
                "the plain image viewer stays listed as an alternate");
        assertEquals(HAL.ZephyrEditor.asNode(), r.editor());
    }

    @Test
    void turtleTypedAsStackOpensInZephyr() {
        // The real listing case: mediaType text/turtle AND the scanner's
        // discovered zeph:Stack type. The conjunctive binding must beat the
        // defaults' text/* source view, which survives as an alternate.
        MediaBindings.Resolved r = bindings().resolve("text/turtle",
                Set.of(ZEPH.NS + "Stack"));
        assertNotNull(r);
        assertEquals(HAL.ZephyrViewer.asNode(), r.viewer(),
                "typed stack Turtle opens in Zephyr, not the text view");
        assertTrue(r.alternates().contains(VG.HtmlTextViewer.asNode()),
                "the source view stays available as an alternate");
        assertEquals(HAL.ZephyrEditor.asNode(), r.editor());
    }

    @Test
    void plainTurtleKeepsTheSourceView() {
        MediaBindings.Resolved r = bindings().resolve("text/turtle", Set.of());
        assertEquals(VG.HtmlTextViewer.asNode(), r.viewer(),
                "an untyped (or not-yet-scanned) Turtle document stays text");
    }

    @Test
    void beakGraphHdf5OpensInZephyr() {
        // BeakGraph feature sets have no browser rendering, but the IIIF
        // engine tiles them — Zephyr is their viewer, under either recorded
        // spelling of the HDF media type.
        for (String mt : java.util.List.of("application/x-hdf5", "application/x-hdf")) {
            MediaBindings.Resolved r = bindings().resolve(mt, Set.of());
            assertNotNull(r, mt + " must resolve to a viewer");
            assertEquals(HAL.ZephyrViewer.asNode(), r.viewer(), mt);
            assertEquals(HAL.ZephyrEditor.asNode(), r.editor(), mt);
        }
    }

    @Test
    void ordinaryImagesKeepTheDefaultViewer() {
        MediaBindings.Resolved r = bindings().resolve("image/png", Set.of());
        assertEquals(VG.HtmlImageViewer.asNode(), r.viewer(),
                "the overlay must not disturb the vandegraph defaults");
    }

    @Test
    void storedHtmlRendersAsAPageWithSourceAndEditorBound() {
        MediaBindings.Resolved r = bindings().resolve("text/html", Set.of());
        assertEquals(HAL.HtmlPageViewer.asNode(), r.viewer(),
                "exact text/html beats the defaults' text/* source view");
        assertTrue(r.alternates().contains(VG.HtmlTextViewer.asNode()),
                "the source view stays available as an alternate");
        assertEquals(HAL.HtmlPageEditor.asNode(), r.editor(),
                "the TipTap document editor is the bound editor for HTML");
    }

    @Test
    void xhtmlRendersAsAPageButIsNotEditable() {
        MediaBindings.Resolved r = bindings().resolve("application/xhtml+xml", Set.of());
        assertEquals(HAL.HtmlPageViewer.asNode(), r.viewer());
        assertTrue(r.alternates().contains(VG.HtmlTextViewer.asNode()),
                "the source view stays available as an alternate");
        assertNull(r.editor(),
                "TipTap serializes HTML, not guaranteed-well-formed XHTML — no editor");
    }
}
