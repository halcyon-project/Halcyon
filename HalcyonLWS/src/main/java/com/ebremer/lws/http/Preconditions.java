package com.ebremer.lws.http;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Conditional requests: {@code If-Match}, {@code If-None-Match},
 * {@code If-Modified-Since}.
 *
 * <p><strong>{@link #evaluate} and {@link #requirePrecondition} must be called inside
 * the write transaction.</strong> Checking the entity tag in a read transaction and then
 * applying the change in a write transaction is a time-of-check/time-of-use race:
 * two clients can both read the same tag, both find it current, and both write —
 * and the second silently destroys the first. Comparing inside the write
 * transaction, which TDB2 serializes to a single writer, turns the whole operation
 * into a genuine compare-and-swap. That is the entire point of the precondition,
 * and it is lost if the check happens anywhere else.
 */
public final class Preconditions {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US)
                    .withZone(java.time.ZoneOffset.UTC);

    private Preconditions() {
    }

    /**
     * Evaluate the preconditions a state-changing request carries, and refuse it with 412 if
     * one is false. A request that carries none proceeds.
     *
     * <p>This is the rule for every write lws10-core defines. Its drafts once made the
     * validator mandatory — "MUST reject unconditional PUT requests that lack an If-Match
     * header with a 428", and the same for a linkset PUT/PATCH — and #228 (14 September 2026)
     * took both out: clients "SHOULD use conditional requests as defined in [RFC9110]", and
     * the server's obligation is only that a precondition which fails is rejected with 412.
     * So a client that sends a stale tag is refused, and one that sends none gets its write;
     * the compare-and-swap is undiminished for everyone who asks for it, because it was only
     * ever the client's to ask for.
     *
     * <p>RFC 9110 §13.2.2, steps 1 and 3 — the two that apply to a method other than GET or
     * HEAD. {@code If-Match} is evaluated when present, by strong comparison, {@code *} being
     * true exactly when a current representation exists; only when it is absent is
     * {@code If-None-Match} evaluated, by weak comparison, and on a state-changing request a
     * false {@code If-None-Match} is a 412 (§13.1.2) — which is what makes
     * {@code If-None-Match: *} "create it, but never overwrite".
     *
     * @param currentEtag the tag as read <em>inside the write transaction</em>, or {@code null}
     *     when there is no current representation (a PUT that would create)
     */
    public static void evaluate(HttpServletRequest req, String currentEtag) {
        String ifMatch = req.getHeader("If-Match");
        if (present(ifMatch)) {
            boolean holds = "*".equals(ifMatch.trim())
                    ? currentEtag != null
                    : matches(ifMatch, currentEtag, true);
            if (!holds) {
                throw failed(currentEtag == null
                        ? "If-Match names a representation, and there is none"
                        : "the resource has changed since it was read", currentEtag);
            }
            return;
        }
        String ifNoneMatch = req.getHeader("If-None-Match");
        if (present(ifNoneMatch)) {
            boolean matched = "*".equals(ifNoneMatch.trim())
                    ? currentEtag != null
                    : matches(ifNoneMatch, currentEtag, false);
            if (matched) {
                throw failed("If-None-Match: the resource exists in that state", currentEtag);
            }
        }
    }

    /**
     * {@link #evaluate}, and a 428 when the request carries no precondition at all.
     *
     * <p>Only for writes this storage defines and lws10-core does not — replacing an
     * access-control resource. A lost update there is not a lost edit but a silently changed
     * authorization decision, so the validator stays mandatory: an agent cannot rewrite a
     * policy without first having read the one it replaces.
     *
     * @param currentEtag the tag as read <em>inside the write transaction</em>
     */
    public static void requirePrecondition(HttpServletRequest req, String currentEtag) {
        if (!present(req.getHeader("If-Match")) && !present(req.getHeader("If-None-Match"))) {
            throw Problem.preconditionRequired(
                    "If-Match is required; GET the resource for its current ETag")
                    .header("ETag", currentEtag);
        }
        evaluate(req, currentEtag);
    }

    private static Problem failed(String detail, String currentEtag) {
        Problem p = Problem.preconditionFailed(detail);
        return currentEtag == null ? p : p.header("ETag", currentEtag);
    }

    private static boolean present(String header) {
        return header != null && !header.isBlank();
    }

    /**
     * True if the client already holds this representation and should get a 304.
     *
     * <p>{@code If-None-Match} wins over {@code If-Modified-Since} when both are
     * present: an entity tag is exact, a timestamp has one-second resolution.
     */
    public static boolean isNotModified(HttpServletRequest req, String etag, Instant modified) {
        String inm = req.getHeader("If-None-Match");
        if (inm != null && !inm.isBlank()) {
            return "*".equals(inm.trim()) || matches(inm, etag, false);
        }
        String ims = req.getHeader("If-Modified-Since");
        if (ims != null && !ims.isBlank() && modified != null) {
            try {
                Instant since = Instant.from(HTTP_DATE.parse(ims.trim()));
                // HTTP dates have one-second resolution, so compare truncated.
                return !modified.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).isAfter(since);
            } catch (DateTimeParseException e) {
                // An unparseable date is ignored, per RFC 9110.
                return false;
            }
        }
        return false;
    }

    /**
     * Does a comma-separated list of entity tags contain this one?
     *
     * <p>RFC 9110 §8.8.3.2: {@code If-Match} compares strongly, so a weak tag in it never
     * matches; {@code If-None-Match} compares weakly, so {@code W/"x"} matches {@code "x"}.
     * This storage mints only strong tags, so the weak marker is all that differs.
     */
    private static boolean matches(String header, String etag, boolean strong) {
        if (etag == null) {
            return false;
        }
        for (String candidate : header.split(",")) {
            String c = candidate.trim();
            if (c.startsWith("W/")) {
                if (strong) {
                    continue;
                }
                c = c.substring(2);
            }
            if (c.equals(etag)) {
                return true;
            }
        }
        return false;
    }

    public static String httpDate(Instant when) {
        return HTTP_DATE.format(when);
    }
}
