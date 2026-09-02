package com.ebremer.halcyon.lws;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link PreviewKind}: how the host classifies a media type, and — the
 * security half — that actively scriptable types are never relayed for
 * same-origin rendering: HTML/XHTML only ever sandbox-relayed, SVG source-viewed.
 */
class PreviewKindTest {

    @Test
    void passiveMediaGetNativeViewers() {
        assertEquals(PreviewKind.IMAGE, PreviewKind.of("image/png"));
        assertEquals(PreviewKind.IMAGE, PreviewKind.of("image/jpeg"));
        assertEquals(PreviewKind.VIDEO, PreviewKind.of("video/mp4"));
        assertEquals(PreviewKind.AUDIO, PreviewKind.of("audio/mpeg"));
        assertEquals(PreviewKind.PDF, PreviewKind.of("application/pdf"));
    }

    @Test
    void textLikeTypesGetTheEscapedTextViewer() {
        assertEquals(PreviewKind.TEXT, PreviewKind.of("text/plain"));
        assertEquals(PreviewKind.TEXT, PreviewKind.of("text/turtle"));
        assertEquals(PreviewKind.TEXT, PreviewKind.of("application/json"));
        assertEquals(PreviewKind.TEXT, PreviewKind.of("application/lws+json"));
        assertEquals(PreviewKind.TEXT, PreviewKind.of("application/ld+json"));
        assertEquals(PreviewKind.TEXT, PreviewKind.of("application/rdf+xml"));
        assertEquals(PreviewKind.TEXT, PreviewKind.of("application/n-triples"));
        assertEquals(PreviewKind.TEXT, PreviewKind.of("TEXT/PLAIN; charset=UTF-8"),
                "case and parameters must not matter");
    }

    @Test
    void htmlIsRenderableButOnlyEverSandboxed() {
        assertEquals(PreviewKind.HTML, PreviewKind.of("text/html"));
        assertEquals(PreviewKind.HTML, PreviewKind.of("text/html; charset=utf-8"));
        assertEquals(PreviewKind.HTML, PreviewKind.of("application/xhtml+xml"));
        // Renderable through the relay — but never as passive media: the relay
        // stamps CSP sandbox on exactly the sandboxRenderable() kinds.
        assertTrue(PreviewKind.HTML.sandboxRenderable());
        assertFalse(PreviewKind.HTML.relayable(),
                "HTML relayed as-is would be a same-origin render of stored markup");
    }

    @Test
    void scriptableTypesAreNeverRelayedPlain() {
        assertEquals(PreviewKind.TEXT, PreviewKind.of("image/svg+xml"),
                "SVG is an image by name and a script host by nature; show source");
        assertFalse(PreviewKind.TEXT.relayable());
        assertFalse(PreviewKind.TEXT.sandboxRenderable());
        assertFalse(PreviewKind.NONE.relayable());
        assertTrue(PreviewKind.IMAGE.relayable());
        assertTrue(PreviewKind.PDF.relayable());
        assertFalse(PreviewKind.IMAGE.sandboxRenderable(),
                "passive media relay plain, not sandboxed — the PDF viewer would refuse");
    }

    @Test
    void unknownsFallToMetadataOnly() {
        assertEquals(PreviewKind.NONE, PreviewKind.of("application/octet-stream"));
        assertEquals(PreviewKind.NONE, PreviewKind.of("application/zip"));
        assertEquals(PreviewKind.NONE, PreviewKind.of(null));
        assertEquals(PreviewKind.NONE, PreviewKind.of("  "));
    }
}
