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

    /**
     * The other direction: file extension (lower-case, no dot) → media type, for the
     * specialist formats the JDK's own name map has never heard of. {@code .svs} and
     * {@code .ndpi} are TIFF containers, so they record as {@code image/tiff} —
     * which is also what makes media-type-driven consumers (the UI's viewer
     * bindings included) treat an adopted whole-slide image as imagery instead of
     * an opaque {@code application/octet-stream} blob.
     */
    private static final Map<String, String> TYPE = Map.ofEntries(
            Map.entry("svs", "image/tiff"),
            Map.entry("ndpi", "image/tiff"),
            Map.entry("tif", "image/tiff"),
            Map.entry("tiff", "image/tiff"),
            Map.entry("jp2", "image/jp2"),
            Map.entry("jxl", "image/jxl"),
            Map.entry("dcm", "application/dicom"),
            Map.entry("h5", "application/x-hdf5"),
            Map.entry("ttl", "text/turtle"),
            Map.entry("nt", "application/n-triples"),
            Map.entry("jsonld", "application/ld+json"));

    private MediaTypeFormats() {
    }

    /**
     * The media type this module knows for a file <em>name</em>, or {@code null}
     * when the extension is absent or unknown — the caller then falls back to the
     * JDK's guess and its own default. Consulted by the mirror gateway's adoption
     * path before {@code URLConnection.guessContentTypeFromName}.
     */
    public static String mediaTypeForName(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return TYPE.get(filename.substring(dot + 1).toLowerCase(Locale.ROOT));
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
