package com.ebremer.lws.store.naming;

import com.ebremer.lws.config.LwsStorageConfig;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Sanitises a client's {@code Slug} into a safe URI path segment.
 *
 * <p>Worth being precise about what this is and is not defending. Because the content store
 * shards blobs under an internal UUID, a slug <em>never reaches a filesystem path</em> — so this
 * is not path-traversal defence, and the Windows device names below are not a filesystem concern
 * either. What a slug can still do is forge a <em>URI</em>, and there are two distinct ways:
 *
 * <ol>
 *   <li><strong>Escape its container.</strong> A segment containing {@code /} or {@code ..} would
 *       mint an identifier outside the container it was created in, breaking the invariant that a
 *       resource sits under its parent.</li>
 *   <li><strong>Impersonate an auxiliary resource.</strong> A name ending in {@code .meta} or
 *       {@code .acr} would be read back by {@code Target.resolve} as the linkset or access control
 *       resource of some <em>other</em> primary — one that does not exist. The resource would be
 *       created and then be permanently unreachable.</li>
 * </ol>
 *
 * <p>The second is the reason the reserved-suffix rule is not merely cosmetic, and it is worth
 * noting that the two hazards need <em>opposite</em> fixes: a whole-name collision (the Windows
 * device names) is neutralised by prefixing, a suffix collision only by appending.
 *
 * <p>The Windows rules are kept even though nothing here touches a filesystem. They cost nothing,
 * and they keep the storage exportable: a URI that cannot be written to a Windows filesystem is a
 * URI that cannot be mirrored to one either.
 */
public final class Slugs {

    /** Anything that is not an unreserved URI character, per RFC 3986. */
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._~-]+");

    private static final Pattern DOT_RUNS = Pattern.compile("\\.{2,}");

    /** Windows device names, which remain reserved regardless of extension. */
    private static final Set<String> RESERVED = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    /**
     * Suffixes by which a URI is recognised as a resource's <em>auxiliary</em> resource
     * rather than as a resource in its own right.
     *
     * <p>A name may not end in one of these. If it could, {@code Slug: report.meta} would
     * mint {@code …/notes/report.meta}, which {@code Target.resolve} would then read as the
     * linkset of {@code …/notes/report} — a primary resource that does not exist. The POST
     * would answer 201 with a Location, the resource would be counted in {@code totalItems}
     * and would hold a blob, and it could never afterwards be read, updated or deleted.
     */
    private static final List<String> AUX_SUFFIXES =
            List.of(LwsStorageConfig.LINKSET_SUFFIX, LwsStorageConfig.ACR_SUFFIX);

    private static final int MAX_LENGTH = 128;

    private Slugs() {
    }

    /**
     * Reduce a slug to a safe segment, or return {@code null} if nothing usable
     * survives.
     *
     * <p>Returning {@code null} is not a failure: the spec is explicit that a
     * server "may use this hint but is not required to", so an unusable slug simply
     * means the server mints a name instead.
     */
    public static String sanitize(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }

        // Decompose accents so they survive as ASCII rather than being dropped.
        String s = Normalizer.normalize(slug.trim(), Normalizer.Form.NFKD);

        // Strip control characters, then collapse anything else unsafe to '-'.
        s = s.replaceAll("\\p{Cntrl}", "");
        s = UNSAFE.matcher(s).replaceAll("-");

        // '..' would climb the URI hierarchy. Collapse any run of dots to one.
        s = DOT_RUNS.matcher(s).replaceAll(".");

        // Leading dots are how the storage reserves its own names (.description,
        // .types/…, and the .meta / .acr suffixes). Stripping them here is what
        // makes those names uncollidable without any escaping scheme.
        s = s.replaceAll("^[.\\-]+", "");

        // Trailing dots and spaces are silently dropped by Windows, which would
        // make two distinct URIs collide on export.
        s = s.replaceAll("[.\\s]+$", "");

        if (s.length() > MAX_LENGTH) {
            s = s.substring(0, MAX_LENGTH);
            s = s.replaceAll("[.\\-]+$", "");
        }
        if (s.isBlank()) {
            return null;
        }

        String stem = s;
        int dot = stem.indexOf('.');
        if (dot > 0) {
            stem = stem.substring(0, dot);
        }
        if (RESERVED.contains(stem.toLowerCase(Locale.ROOT))) {
            s = "_" + s;
        }

        // An auxiliary suffix must be neutralised LAST, and by appending rather than
        // prefixing.
        //
        // Prefixing is what the Windows device names above need, because those collide on
        // the whole name. It is useless here: "_report.meta" still ends in ".meta" and is
        // still parsed as a linkset. A suffix collision needs a suffix fix.
        //
        // It also has to happen after the truncation, since cutting a long name at
        // MAX_LENGTH can land the cut so that the remainder happens to end in ".meta".
        //
        // Appending keeps the invariant true under SlugNaming's collision disambiguation,
        // which splits on the LAST dot and reattaches the tail verbatim: "report.meta_"
        // becomes "report-1.meta_", never "report-1.meta". A name that does not end in an
        // auxiliary suffix therefore cannot acquire one later.
        while (endsWithAuxSuffix(s)) {
            s = s + "_";
        }
        return s;
    }

    /** Case-insensitive on purpose: it should not matter if {@code Target} ever stops being. */
    private static boolean endsWithAuxSuffix(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        return AUX_SUFFIXES.stream().anyMatch(x -> lower.endsWith(x.toLowerCase(Locale.ROOT)));
    }

    /** The file extension implied by a slug, including the dot, or {@code ""}. */
    public static String extensionOf(String slug) {
        if (slug == null) {
            return "";
        }
        String s = sanitize(slug);
        if (s == null) {
            return "";
        }
        int dot = s.lastIndexOf('.');
        if (dot <= 0 || dot == s.length() - 1) {
            return "";
        }
        String ext = s.substring(dot);
        // Guard against a pathological "extension" that is really a whole name.
        return ext.length() > 16 ? "" : ext.toLowerCase(Locale.ROOT);
    }
}
