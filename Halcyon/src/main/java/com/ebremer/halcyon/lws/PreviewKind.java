package com.ebremer.halcyon.lws;

import java.util.Locale;

/**
 * The host-side classification of a media type for the preview panel — the
 * part of media dispatch that must never be data.
 *
 * <p>Three service levels:
 * <ul>
 *   <li><b>{@linkplain #relayable() Relayable}</b> — passive media (img /
 *       video / audio / the PDF viewer): the token relay serves the bytes
 *       to the browser as-is for native rendering.</li>
 *   <li><b>{@linkplain #sandboxRenderable() Sandbox-renderable}</b> — HTML
 *       and XHTML: relayed <em>only</em> under
 *       {@code Content-Security-Policy: sandbox}, and rendered inside a
 *       {@code sandbox=""} iframe. The page displays, but as a unique
 *       opaque origin with no script — never as this site. Relaying these
 *       plain would hand any uploader a stored XSS, which is exactly what
 *       the LWS serving path itself also refuses since it answers
 *       scriptable types with the same sandbox policy.</li>
 *   <li><b>{@link #TEXT}</b> — textual types: fetched server-side, bounded,
 *       and rendered <em>escaped</em>. SVG — image by name, script host by
 *       nature — stays here on purpose: its default view is source.</li>
 * </ul>
 *
 * <p>Since the vandegraph {@code vg:MediaBinding} shapes took over
 * <em>viewer selection</em>, this enum's job is the relay policy and the
 * text-preview heuristic. Bindings decide what to show; this decides what
 * the relay will serve, and under which policy.
 */
public enum PreviewKind {
    IMAGE, VIDEO, AUDIO, PDF, HTML, TEXT, NONE;

    /** May the relay serve this inline to the browser as-is? Passive media only. */
    public boolean relayable() {
        return this == IMAGE || this == VIDEO || this == AUDIO || this == PDF;
    }

    /**
     * May the relay serve this for <em>sandboxed</em> rendering? The relay
     * stamps {@code Content-Security-Policy: sandbox} on these responses —
     * they render as documents, but never as this origin and never with
     * script.
     */
    public boolean sandboxRenderable() {
        return this == HTML;
    }

    /** The classification of a media type ({@code null}/blank → {@link #NONE}). */
    public static PreviewKind of(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return NONE;
        }
        String mt = mediaType.trim().toLowerCase(Locale.ROOT);
        int semi = mt.indexOf(';');
        if (semi >= 0) {
            mt = mt.substring(0, semi).trim();
        }
        if (mt.equals("text/html") || mt.equals("application/xhtml+xml")) {
            return HTML;   // renderable, but only ever sandboxed
        }
        if (mt.equals("image/svg+xml")) {
            return TEXT;   // scriptable; source view by default (see class comment)
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
