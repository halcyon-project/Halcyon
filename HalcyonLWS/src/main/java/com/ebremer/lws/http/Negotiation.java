package com.ebremer.lws.http;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.jena.riot.RDFFormat;

/**
 * Content negotiation for the LWS documents this storage builds — container listings, the type
 * index, a search page, a subscription, an access request or grant: which representation an
 * {@code Accept} header selects, and whether the response names the LWS profile.
 *
 * <p>Each of those documents is one {@code application/lws+json} object. Its representations are
 * that object labelled {@code application/lws+json}, {@code application/ld+json} or
 * {@code application/json} (the same bytes), or its RDF written as Turtle, N-Triples, N-Quads or
 * TriG.
 *
 * <p><strong>A container's representations are in the LWS profile</strong> (lws10-core, LWS Profile
 * and Content Negotiation; w3c/lws-protocol#253): "A client requests a representation in the LWS
 * profile by including the LWS profile URI in the {@code profile} parameter of an RDF media type ...
 * If the server selects such a media type, the response MUST conform to the LWS profile, that is, it
 * MUST be RDF isomorphic to the {@code application/lws+json} representation, and its
 * {@code Content-Type} MUST include the same {@code profile} parameter. A server MUST NOT select such
 * a media type if it cannot produce a representation in the LWS profile in it." Every container
 * representation here is in the profile — the JSON forms are the lws+json document itself, and the
 * RDF is that document read as JSON-LD ({@link com.ebremer.lws.json.LwsRdf#profileRdf}) — so a
 * media range is honoured whether or not it asks for the profile, and the {@code Content-Type} names
 * the profile exactly when the range that selected an RDF media type asked for it. A range whose
 * {@code profile} lists only other URIs (JSON-LD's {@code http://www.w3.org/ns/json-ld#compacted},
 * say) asks for a representation this storage cannot produce, so it matches nothing, and the
 * client's other ranges decide. The profile is defined for container representations only, so
 * for every other document a range carrying a {@code profile} parameter matches nothing at all.
 *
 * <p>The choice is RFC 9110 &sect;12.5.1's. Each representation takes the quality of the
 * <em>most specific</em> range that matches it — a range with a {@code profile} parameter being
 * more specific than the same range without one — and {@code q=0} refuses it. The highest quality
 * wins, and the server's order breaks a tie: {@code application/lws+json}, {@code application/ld+json},
 * {@code application/json}, Turtle, N-Triples, N-Quads, TriG. So an absent {@code Accept},
 * {@code *}&#47;{@code *} and {@code application/*} get {@code application/lws+json}, the one
 * representation lws10-core requires of a container ("Servers MUST honor requests for
 * {@code application/lws+json} on containers"), and a client that ranks Turtle above it gets
 * Turtle. An {@code Accept} that matches none of them is a {@code 406}.
 *
 * <p>Until the drafts of 19 October 2026 the three JSON types were equivalent and required, and
 * Turtle was served only to a client that would take no JSON type at all, whatever the q-values
 * said. #253 removed the equivalence; only {@code application/lws+json} is required now, and the
 * RDF types are ordinary negotiated alternatives.
 */
public final class Negotiation {

    private Negotiation() {
    }

    /** An RDF syntax a document's RDF can be written in. */
    public enum Syntax {
        TURTLE(MediaTypes.TURTLE, RDFFormat.TURTLE_PRETTY, "ttl"),
        N_TRIPLES(MediaTypes.N_TRIPLES, RDFFormat.NTRIPLES, "nt"),
        /** The document's RDF is one graph, written as the default graph. */
        N_QUADS(MediaTypes.N_QUADS, RDFFormat.NQUADS, "nq"),
        /** The document's RDF is one graph, written as the default graph. */
        TRIG(MediaTypes.TRIG, RDFFormat.TRIG_PRETTY, "trig");

        private final String mediaType;
        private final RDFFormat format;
        private final String marker;

        Syntax(String mediaType, RDFFormat format, String marker) {
            this.mediaType = mediaType;
            this.format = format;
            this.marker = marker;
        }

        public String mediaType() {
            return mediaType;
        }

        /** The Jena output format. */
        public RDFFormat format() {
            return format;
        }
    }

    /**
     * The representation an {@code Accept} header selected.
     *
     * @param mediaType the media type, without parameters
     * @param syntax    the RDF syntax the document's RDF is written in, or {@code null} for the JSON
     *                  document itself
     * @param profile   whether the {@code Content-Type} names the LWS profile
     */
    public record Variant(String mediaType, Syntax syntax, boolean profile) {

        /** Whether the document's RDF is written out, rather than its JSON sent. */
        public boolean isRdf() {
            return syntax != null;
        }

        /**
         * The {@code Content-Type}, without a charset: the media type, and the {@code profile}
         * parameter when the client asked for the profile. Only the LWS profile URI is named, even
         * when the client listed others beside it: it is the one this representation is in.
         */
        public String contentType() {
            return profile ? mediaType + "; profile=\"" + MediaTypes.LWS_PROFILE + "\"" : mediaType;
        }

        /**
         * This representation's entity tag, given the JSON document's. The three JSON media types
         * label the same bytes and share it; each RDF syntax is a different representation and gets
         * its own — the tag with the syntax's marker before the closing quote ({@code "abc"} becomes
         * {@code "abc-ttl"}) — so a client holding one is never answered {@code 304} for another it
         * has not seen. Naming the profile changes the {@code Content-Type}, not the bytes, so the
         * marker does not depend on it.
         */
        public String tag(String etag) {
            if (syntax == null || etag == null || etag.length() < 2 || !etag.endsWith("\"")) {
                return etag;
            }
            return etag.substring(0, etag.length() - 1) + "-" + syntax.marker + "\"";
        }
    }

    /** One representation on offer, and whether its media type defines the {@code profile} parameter. */
    private record Offer(String mediaType, Syntax syntax, boolean definesProfile) {
    }

    /**
     * Every representation of an LWS document, in the server's order of preference. Only the RDF
     * media types define {@code profile}: {@code application/lws+json} is the profile's own form,
     * and {@code application/json} has no parameters.
     */
    private static final List<Offer> OFFERS = List.of(
            new Offer(MediaTypes.LWS_JSON, null, false),
            new Offer(MediaTypes.LD_JSON, null, true),
            new Offer(MediaTypes.JSON, null, false),
            new Offer(MediaTypes.TURTLE, Syntax.TURTLE, true),
            new Offer(MediaTypes.N_TRIPLES, Syntax.N_TRIPLES, true),
            new Offer(MediaTypes.N_QUADS, Syntax.N_QUADS, true),
            new Offer(MediaTypes.TRIG, Syntax.TRIG, true));

    private static final Variant DEFAULT = new Variant(MediaTypes.LWS_JSON, null, false);

    /**
     * The representation of a container — or of a listing that is one, the notification service's
     * or the access request and grant endpoints' — for {@code accept}: in the LWS profile.
     *
     * @throws Problem 406 when {@code accept} matches none of them
     */
    public static Variant container(String accept) {
        return select(accept, true);
    }

    /**
     * The representation of an LWS document that is not a container representation — a
     * subscription, an access request or grant, the type index, a page of search results — for
     * {@code accept}: in no profile.
     *
     * @throws Problem 406 when {@code accept} matches none of them
     */
    public static Variant document(String accept) {
        return select(accept, false);
    }

    private static Variant select(String accept, boolean inLwsProfile) {
        if (accept == null || accept.isBlank()) {
            return DEFAULT;
        }
        List<Range> ranges = parse(accept);
        Variant best = null;
        double bestQ = 0.0;
        for (Offer o : OFFERS) {
            Match m = match(o.mediaType(), ranges, inLwsProfile);
            // Strictly greater: on a tie the earlier offer, which the server prefers, stays.
            if (m.q() > bestQ) {
                best = new Variant(o.mediaType(), o.syntax(), m.viaProfile() && o.definesProfile());
                bestQ = m.q();
            }
        }
        if (best == null) {
            throw Problem.notAcceptable("this resource is available as " + MediaTypes.LWS_JSON + ", "
                    + MediaTypes.LD_JSON + ", " + MediaTypes.JSON + ", " + MediaTypes.TURTLE + ", "
                    + MediaTypes.N_TRIPLES + ", " + MediaTypes.N_QUADS + " or " + MediaTypes.TRIG
                    + (inLwsProfile ? ", in the LWS profile (" + MediaTypes.LWS_PROFILE + ")"
                            : ", in no profile"));
        }
        return best;
    }

    /** How a client ranks one representation, and whether a range naming the LWS profile decided it. */
    private record Match(double q, boolean viaProfile) {
    }

    /**
     * The quality {@code ranges} give {@code mediaType}, from the most specific range that matches
     * it (RFC 9110 &sect;12.5.1: {@code text/turtle;q=0.7, text/*;q=0.3} is 0.7 for Turtle).
     *
     * <p>A range with a {@code profile} parameter matches only a representation in one of the
     * profiles it lists (RFC 6906; a range's parameters narrow it), so it matches a container
     * representation only when the LWS profile is among them, and nothing else ever. It is more
     * specific than the same range without the parameter and less specific than a narrower range:
     * {@code text/turtle;profile=...} beats {@code text/turtle}, which beats
     * {@code text/*;profile=...}. Between equally specific ranges the higher quality counts, which
     * makes a repeated range harmless.
     */
    private static Match match(String mediaType, List<Range> ranges, boolean inLwsProfile) {
        int slash = mediaType.indexOf('/');
        String type = mediaType.substring(0, slash);
        String subtype = mediaType.substring(slash + 1);
        int best = -1;
        double q = 0.0;
        boolean viaProfile = false;
        for (Range r : ranges) {
            boolean typeOk = r.type().equals("*") || r.type().equals(type);
            boolean subtypeOk = r.subtype().equals("*") || r.subtype().equals(subtype);
            if (!typeOk || !subtypeOk) {
                continue;
            }
            if (r.profiles() != null && !(inLwsProfile && r.profiles().contains(MediaTypes.LWS_PROFILE))) {
                continue;
            }
            int breadth = r.type().equals("*") ? 0 : r.subtype().equals("*") ? 1 : 2;
            int specificity = 2 * breadth + (r.profiles() != null ? 1 : 0);
            if (specificity > best || (specificity == best && r.q() > q)) {
                best = specificity;
                q = r.q();
                viaProfile = r.profiles() != null;
            }
        }
        return new Match(q, viaProfile);
    }

    /**
     * One media range of an {@code Accept} header.
     *
     * @param profiles the URIs its {@code profile} parameter lists, or {@code null} when it has none
     */
    private record Range(String type, String subtype, double q, Set<String> profiles) {
    }

    /**
     * The media ranges of an {@code Accept} header. Types and parameter names are case-insensitive
     * (RFC 9110 &sect;8.3.1, &sect;5.6.6) and folded; parameter values are not, since a profile is a
     * URI. A {@code profile} value is a whitespace-separated list of URIs (RFC 6906), quoted when it
     * holds more than one and accepted unquoted when it is a lone URI, as it is commonly sent.
     */
    static List<Range> parse(String accept) {
        List<Range> out = new ArrayList<>();
        for (String element : splitOutsideQuotes(accept, ',')) {
            List<String> parts = splitOutsideQuotes(element, ';');
            String range = parts.get(0).trim().toLowerCase(Locale.ROOT);
            if (range.isEmpty()) {
                continue;
            }
            int slash = range.indexOf('/');
            String type = slash < 0 ? range : range.substring(0, slash).trim();
            String subtype = slash < 0 ? "*" : range.substring(slash + 1).trim();
            double q = 1.0;
            Set<String> profiles = null;
            for (int i = 1; i < parts.size(); i++) {
                String p = parts.get(i).trim();
                int eq = p.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String name = p.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String value = unquote(p.substring(eq + 1).trim());
                if (name.equals("q")) {
                    try {
                        q = Math.max(0.0, Math.min(1.0, Double.parseDouble(value)));
                    } catch (NumberFormatException e) {
                        q = 1.0;
                    }
                } else if (name.equals("profile")) {
                    if (profiles == null) {
                        profiles = new LinkedHashSet<>();
                    }
                    for (String uri : value.split("\\s+")) {
                        if (!uri.isEmpty()) {
                            profiles.add(uri);
                        }
                    }
                }
            }
            out.add(new Range(type.isEmpty() ? "*" : type, subtype.isEmpty() ? "*" : subtype, q,
                    profiles));
        }
        return out;
    }

    /** Split on {@code separator} wherever it is not inside a quoted string (RFC 9110 &sect;5.6.4). */
    private static List<String> splitOutsideQuotes(String s, char separator) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quoted && c == '\\' && i + 1 < s.length()) {
                current.append(c).append(s.charAt(++i));
                continue;
            }
            if (c == '"') {
                quoted = !quoted;
            } else if (c == separator && !quoted) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        parts.add(current.toString());
        return parts;
    }

    /** The content of a quoted string with its quoted-pairs undone; a token is returned as it is. */
    private static String unquote(String value) {
        if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 1; i < value.length() - 1; i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length() - 1) {
                c = value.charAt(++i);
            }
            out.append(c);
        }
        return out.toString();
    }
}
