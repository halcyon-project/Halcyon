package com.ebremer.lws.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The media-type → extension fallback — {@link MediaTypeFormats} (M2). It only ever runs when a slug
 * gave no extension, and the scanner still gates the result through the reader registry, so the one
 * thing that must hold is that a known type maps to the extension a reader recognises and an unknown
 * or opaque type maps to nothing.
 */
class MediaTypeFormatsTest {

    @Test
    void knownMediaTypesMapToAReaderExtension() {
        assertEquals(".tif", MediaTypeFormats.extensionFor("image/tiff"));
        assertEquals(".jp2", MediaTypeFormats.extensionFor("image/jp2"));
        assertEquals(".jxl", MediaTypeFormats.extensionFor("image/jxl"));
        assertEquals(".dcm", MediaTypeFormats.extensionFor("application/dicom"));
        assertEquals(".ttl", MediaTypeFormats.extensionFor("text/turtle"));
        assertEquals(".nt", MediaTypeFormats.extensionFor("application/n-triples"));
    }

    @Test
    void mappingIsCaseInsensitiveAndTrimmed() {
        assertEquals(".tif", MediaTypeFormats.extensionFor("IMAGE/TIFF"));
        assertEquals(".tif", MediaTypeFormats.extensionFor("  image/tiff  "));
    }

    @Test
    void opaqueOrUnknownTypesMapToNothing() {
        assertEquals("", MediaTypeFormats.extensionFor("application/octet-stream"));
        assertEquals("", MediaTypeFormats.extensionFor("text/plain"));
        assertEquals("", MediaTypeFormats.extensionFor("application/x-unknown"));
        assertEquals("", MediaTypeFormats.extensionFor(null));
    }

    @Test
    void everyMappedExtensionCarriesItsLeadingDot() {
        // Matches the convention of Slugs.extensionOf, since the two feed the same code path.
        for (String t : new String[] {"image/tiff", "image/jp2", "image/jxl", "application/dicom",
                "text/turtle", "application/n-triples", "application/ld+json"}) {
            String ext = MediaTypeFormats.extensionFor(t);
            assertEquals('.', ext.charAt(0), () -> t + " -> " + ext + " (missing leading dot)");
        }
    }
}
