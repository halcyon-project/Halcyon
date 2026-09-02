package com.ebremer.halcyon.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jetty.http.pathmap.PathSpecGroup;
import org.eclipse.jetty.http.pathmap.ServletPathSpec;

/**
 * Rules a URL pattern must satisfy before it is trusted to guard anything.
 *
 * <p>This exists because a security filter registered on a pattern that cannot match is
 * indistinguishable, from the outside, from no security filter at all — and Jetty will not tell you.
 * The asymmetry that let it happen: a <em>servlet</em> mapping is parsed through
 * {@link ServletPathSpec}'s constructor, which rejects an illegal spec at startup, but a
 * <em>filter</em> mapping is kept as a raw string and matched with the static
 * {@link ServletPathSpec#match(String, String, boolean)}. So {@code "/iiif*&#47;"} — which the
 * constructor refuses outright ("glob '*' can only exist at end of prefix based matches") — was
 * accepted silently as a filter pattern and degraded to an exact match on the literal seven
 * characters {@code /iiif*&#47;}, which no request path can ever be. The IIIF servlet mounted at
 * {@code /iiif/*} was thereby unguarded, with no error, no warning and no log line at any point.
 *
 * <p>Two rules, because legality alone would not have caught the whole class:
 * <ul>
 *   <li>{@link #assertLegal} — the pattern is a form Servlet 6.0 defines, and is not an exact spec
 *       with a {@code *} in it. Jetty accepts {@code "/f*"} as EXACT and merely logs a "suspicious
 *       URL pattern" warning from a constructor that filters never reach; a {@code *} inside an
 *       exact spec is always a typo for a prefix spec.</li>
 *   <li>{@link #assertCovers} — a pattern that names a servlet's URL space actually covers it.
 *       {@code "/invalidateSession"} is perfectly legal and matches nothing under
 *       {@code /invalidateSession/*}, where the servlet is really mounted.</li>
 * </ul>
 *
 * <p>Both throw rather than warn. The defect being prevented is precisely one that produced no
 * observable signal for however long it existed, so a log line is the wrong remedy: it would join
 * the same silence. A server that cannot enforce the access control it was configured with should
 * not accept traffic.
 */
public final class UrlPatternRules {

    private UrlPatternRules() {
    }

    /** Thrown when a configured pattern cannot do the job it was registered for. */
    public static final class IllegalPatternException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public IllegalPatternException(String message) {
            super(message);
        }
    }

    private static final String LEGAL_FORMS =
            "a legal mapping is one of: a prefix spec beginning \"/\" and ending \"/*\"; "
            + "an extension spec beginning \"*.\"; or an exact path containing no \"*\"";

    /**
     * Every pattern is a well-formed servlet mapping.
     *
     * <p>Form only. {@code "/"} (the default mapping) and {@code "/*"} are perfectly legal here and
     * are what a dispatcher or a framework filter is normally registered on — this is the check to
     * apply to <em>registrations</em>, including ones this project does not own. The stricter
     * question of whether a pattern is fit to <em>guard</em> something is
     * {@link #assertGuardable}, and conflating the two rejects Spring's own
     * {@code dispatcherServlet}.
     *
     * @param where human-readable source of the list, named in any failure
     */
    public static void assertLegal(String where, Collection<String> patterns) {
        for (String p : patterns) {
            if (p == null || p.isBlank()) {
                // The empty mapping is legal in the spec (it is the context root) but nothing in
                // this application registers one, and a blank string is far more often a bug.
                throw new IllegalPatternException(where + ": a blank URL pattern. " + LEGAL_FORMS);
            }
            if ("/".equals(p)) {
                continue;   // the default mapping: legal, and correct for a dispatcher servlet
            }
            ServletPathSpec spec;
            try {
                spec = new ServletPathSpec(p);
            } catch (RuntimeException e) {
                throw new IllegalPatternException(where + ": \"" + p + "\" is not a legal servlet "
                        + "mapping (" + e.getMessage() + "). It would be accepted silently as a "
                        + "filter pattern and match nothing. " + LEGAL_FORMS);
            }
            PathSpecGroup group = spec.getGroup();
            if (group == PathSpecGroup.EXACT && p.indexOf('*') >= 0) {
                throw new IllegalPatternException(where + ": \"" + p + "\" parses as an EXACT path "
                        + "that contains \"*\", so it matches only the literal characters \"" + p
                        + "\" and no real request. This is always a mistyped prefix spec — did you "
                        + "mean \"" + prefixSuggestion(p) + "\"? " + LEGAL_FORMS);
            }
            if (group != PathSpecGroup.EXACT && group != PathSpecGroup.PREFIX_GLOB
                    && group != PathSpecGroup.SUFFIX_GLOB && group != PathSpecGroup.ROOT
                    && group != PathSpecGroup.DEFAULT) {
                throw new IllegalPatternException(where + ": \"" + p + "\" parses as " + group
                        + ", which is not a servlet mapping form. " + LEGAL_FORMS);
            }
        }
    }

    /**
     * Every pattern is fit to guard something.
     *
     * <p>{@link #assertLegal} plus the rules that only make sense for a <em>security</em> list: no
     * default mapping (it would cover the entire application, which is never what a secured-URL
     * list means), and no duplicates. Applied to the lists this project maintains, not to framework
     * registrations — a dispatcher servlet on {@code "/"} is correct and must not be refused.
     */
    public static void assertGuardable(String where, Collection<String> patterns) {
        assertLegal(where, patterns);
        Set<String> seen = new LinkedHashSet<>();
        for (String p : patterns) {
            if ("/".equals(p)) {
                throw new IllegalPatternException(where + ": \"/\" is the default mapping and would "
                        + "cover the entire application; name the paths to guard explicitly");
            }
            if (!seen.add(p)) {
                throw new IllegalPatternException(where + ": \"" + p + "\" is listed twice");
            }
        }
    }

    /** Best-effort "did you mean" for a mistyped prefix spec, e.g. {@code /iiif*​/} -> {@code /iiif/*}. */
    private static String prefixSuggestion(String p) {
        String stripped = p.replace("*", "");
        if (!stripped.startsWith("/")) {
            stripped = "/" + stripped;
        }
        while (stripped.endsWith("/")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped + "/*";
    }

    /**
     * Where a secured pattern and a servlet mapping share a first path segment, the pattern must
     * actually cover the servlet's URL space.
     *
     * <p>The narrow test is deliberate. Requiring every servlet to be guarded would be wrong —
     * plenty are anonymous by design — so this only fires when the configuration has already
     * expressed the intent to guard something under that segment and then failed to. That is
     * exactly the shape of both known defects: {@code "/iiif*&#47;"} against {@code /iiif/*}, and
     * {@code "/invalidateSession"} against {@code /invalidateSession/*}.
     */
    public static void assertCovers(String where, Collection<String> securedPatterns,
            Collection<String> servletMappings) {
        for (String mapping : servletMappings) {
            if (mapping == null || mapping.isBlank() || "/".equals(mapping)) {
                continue;
            }
            String segment = firstSegment(mapping);
            if (segment.isEmpty()) {
                continue;
            }
            List<String> naming = new ArrayList<>();
            for (String p : securedPatterns) {
                if (p != null && !p.isBlank() && firstSegment(p).equals(segment)) {
                    naming.add(p);
                }
            }
            if (naming.isEmpty()) {
                continue;   // nothing claims to guard this segment; not this check's business
            }
            List<String> probes = probesFor(mapping);
            for (String probe : probes) {
                boolean covered = naming.stream()
                        .anyMatch(p -> ServletPathSpec.match(p, probe, true));
                if (!covered) {
                    throw new IllegalPatternException(where + ": " + naming
                            + " name the \"/" + segment + "\" space but do not cover the servlet "
                            + "mapped at \"" + mapping + "\" — the request \"" + probe
                            + "\" would reach it unguarded. Use a prefix spec such as \""
                            + "/" + segment + "/*\".");
                }
            }
        }
    }

    /** Representative paths a request could take against {@code mapping}. */
    private static List<String> probesFor(String mapping) {
        if (mapping.endsWith("/*")) {
            String base = mapping.substring(0, mapping.length() - 2);
            return List.of(base + "/", base + "/probe", base + "/a/b");
        }
        if (mapping.startsWith("*.")) {
            return List.of("/probe" + mapping.substring(1));
        }
        return List.of(mapping);
    }

    private static String firstSegment(String pattern) {
        String s = pattern.startsWith("/") ? pattern.substring(1) : pattern;
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        int star = s.indexOf('*');
        if (star >= 0) {
            s = s.substring(0, star);
        }
        return s;
    }
}
