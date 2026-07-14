package com.ebremer.lws.scan;

import java.util.Locale;
import java.util.Map;

/**
 * Best-effort media type → file extension, for driving the metadata scanner when a client
 * gave no {@code Slug} to derive one from.
 *
 * <p>halcyon-core's {@code FileReaderFactoryProvider} dispatches purely on file extension —
 * that is how it was built — so a blob has to <em>have</em> an extension for any reader to
 * claim it. The extension normally comes from the {@code Slug} ({@code TCGA-AA-3872.svs} →
 * {@code svs}). Without a slug there was nothing to dispatch on, so a whole-slide image
 * POSTed with only {@code Content-Type: image/tiff} was stored perfectly and then invisible
 * to the Type Index. The media type was right there and went unused. (M2.)
 *
 * <p>The mapping is deliberately conservative and its failure mode is benign: it is only ever
 * consulted when the slug yielded nothing, the scanner still gates every extension through
 * {@code FileReaderFactoryProvider.contains}, and a reader that cannot actually parse the bytes
 * throws and is caught. So an over-generous entry costs a skipped scan, never an error.
 *
 * <p>Where a media type is genuinely ambiguous it is mapped to the most general reader that
 * fits: SVS and NDPI are both {@code image/tiff} on the wire but need their own readers, so
 * {@code image/tiff} maps to the plain TIFF reader, which still recovers the base dimensions.
 * A client wanting SVS- or NDPI-specific handling supplies a slug, exactly as before.
 */
public final class MediaTypeFormats {

    /** Media type (bare, lower-case) → extension, including the leading dot to match {@code Slugs}. */
    private static final Map<String, String> EXT = Map.ofEntries(
            Map.entry("image/tiff", ".tif"),
            Map.entry("image/tif", ".tif"),
            Map.entry("image/jp2", ".jp2"),
            Map.entry("image/jpeg2000", ".jp2"),
            Map.entry("image/jpx", ".jp2"),
            Map.entry("image/jxl", ".jxl"),
            Map.entry("application/dicom", ".dcm"),
            Map.entry("application/x-hdf5", ".h5"),
            Map.entry("application/x-hdf", ".h5"),
            Map.entry("text/turtle", ".ttl"),
            Map.entry("application/n-triples", ".nt"),
            Map.entry("application/ld+json", ".jsonld"));

    private MediaTypeFormats() {
    }

    /**
     * The extension a reader would recognise for this media type, or {@code ""} if none is
     * known. The result includes the leading dot, or is empty — the same convention as
     * {@code Slugs.extensionOf}.
     */
    public static String extensionFor(String mediaType) {
        if (mediaType == null) {
            return "";
        }
        return EXT.getOrDefault(mediaType.trim().toLowerCase(Locale.ROOT), "");
    }
}
