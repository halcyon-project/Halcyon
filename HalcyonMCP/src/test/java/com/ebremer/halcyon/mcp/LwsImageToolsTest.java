package com.ebremer.halcyon.mcp;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP-11: the caller guard, the IIIF request-URL construction (image identity
 * confined to its storage's own endpoint, whole URL percent-encoded into the
 * single {@code iiif} parameter), and the edge clamp. The image fetch itself
 * is runtime-verified.
 */
class LwsImageToolsTest {

    @Test
    void bothToolsRefuseWithoutAVerifiedCaller() {
        assertThrows(IllegalStateException.class,
                () -> new LwsImageTools().iiifInfo("https://evil/x", new ToolContext(Map.of())));
        assertThrows(IllegalStateException.class, () -> new LwsImageTools()
                .iiifThumbnail("https://evil/x", 256, new ToolContext(Map.of())));
    }

    @Test
    void requestUrlEncodesTheWholeIiifUrlIntoOneParam() {
        String url = LwsImageTools.iiifRequestUrl(
                "https://s.example/W3Clws/.iiif",
                "https://s.example/W3Clws/slides/a.svs",
                "/full/!512,512/0/default.jpg");
        assertTrue(url.startsWith("https://s.example/W3Clws/.iiif?iiif="),
                "the request goes to the storage's own .iiif endpoint");
        // The slashes and commas of the IIIF URL must be encoded so the servlet
        // reads the whole thing as one parameter value.
        assertTrue(url.contains("%2F") && url.contains("%2C"),
                "the IIIF URL must be percent-encoded into the iiif param: " + url);
        assertTrue(url.contains("slides") == false || url.contains("%2Fslides%2F"),
                "path separators inside the image URI are encoded");
    }

    @Test
    void infoAndThumbnailPathsAreTheIiifApiShapes() {
        String info = LwsImageTools.iiifRequestUrl("E", "IMG", "/info.json");
        assertTrue(info.contains("IMG%2Finfo.json"));
        String thumb = LwsImageTools.iiifRequestUrl("E", "IMG", "/full/!256,256/0/default.jpg");
        assertTrue(thumb.contains("IMG%2Ffull%2F%21256%2C256%2F0%2Fdefault.jpg"),
                "the IIIF Image API request path must survive encoding: " + thumb);
    }

    @Test
    void edgeIsClampedAndDefaulted() {
        assertEquals(512, LwsImageTools.clampEdge(null), "default edge");
        assertEquals(256, LwsImageTools.clampEdge(256));
        assertEquals(Guardrails.MAX_IMAGE_EDGE, LwsImageTools.clampEdge(99999),
                "an oversized request is clamped to the max edge");
        assertEquals(1, LwsImageTools.clampEdge(0), "a non-positive edge floors at 1");
    }
}
