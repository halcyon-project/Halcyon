package com.ebremer.halcyon.lws;

import java.util.Locale;

/**
 * The default viewer the preview panel gives a media type.
 *
 * <p>Only passive media are {@linkplain #relayable() relayed} to the browser
 * for native rendering (img / video / audio / the PDF viewer). Textual types
 * are fetched server-side, bounded, and rendered <em>escaped</em>. Actively
 * scriptable types get source view on purpose: HTML falls under {@code text/*},
 * and SVG — image by name, script host by nature — is forced to {@link #TEXT},
 * because relaying either would serve attacker-uploadable active content from
 * the application's own origin, i.e. hand any uploader a stored XSS. Rendering
 * them stays possible via the direct "open" link, where the storage answers on
 * its own terms.
 *
 * <p>Since the vandegraph {@code vg:MediaBinding} shapes took over <em>viewer
 * selection</em>, this enum's remaining job is the part that must never be
 * data: the relay whitelist ({@link #relayable()}) and the text-preview
 * heuristic. Bindings decide what to show; this decides what the relay will
 * serve.
 */
public enum PreviewKind {
    IMAGE, VIDEO, AUDIO, PDF, TEXT, NONE;

    /** May the relay serve this inline to the browser? Passive media only. */
    public boolean relayable() {
        return this == IMAGE || this == VIDEO || this == AUDIO || this == PDF;
    }

    /** The default viewer for a media type ({@code null}/blank → {@link #NONE}). */
    public static PreviewKind of(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return NONE;
        }
        String mt = mediaType.trim().toLowerCase(Locale.ROOT);
        int semi = mt.indexOf(';');
        if (semi >= 0) {
            mt = mt.substring(0, semi).trim();
        }
        if (mt.equals("image/svg+xml")) {
            return TEXT;   // scriptable; see the class comment
        }
        if (mt.startsWith("image/")) {
            return IMAGE;
        }
        if (mt.startsWith("video/")) {
            return VIDEO;
        }
        if (mt.startsWith("audio/")) {
            return AUDIO;
        }
        if (mt.equals("application/pdf")) {
            return PDF;
        }
        if (mt.startsWith("text/")
                || mt.equals("application/json")
                || mt.equals("application/xml")
                || mt.endsWith("+json")
                || mt.endsWith("+xml")
                || mt.equals("application/n-triples")
                || mt.equals("application/n-quads")
                || mt.equals("application/trig")
                || mt.equals("application/sparql-query")
                || mt.equals("application/sparql-update")) {
            return TEXT;
        }
        return NONE;
    }
}
