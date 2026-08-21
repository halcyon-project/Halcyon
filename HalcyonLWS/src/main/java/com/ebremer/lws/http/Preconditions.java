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
 * <p><strong>{@link #requireIfMatch} and {@link #checkIfMatch} must be called inside
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
     * Enforce the precondition on a state-changing request.
     *
     * <p>lws10-core: if a server supports entity tags for a resource it "MUST reject
     * unconditional PUT requests that lack an If-Match header with a 428 Precondition
     * Required response", and a mismatch is a 412. This storage tags every resource,
     * so an unconditional overwrite is always a 428 — there is no way to clobber a
     * resource without having first read it.
     *
     * @param currentEtag the tag as read <em>inside the write transaction</em>
     */
    public static void requireIfMatch(HttpServletRequest req, String currentEtag) {
        String ifMatch = req.getHeader("If-Match");

        if (ifMatch == null || ifMatch.isBlank()) {
            throw Problem.preconditionRequired(
                    "If-Match is required; GET the resource for its current ETag")
                    .header("ETag", currentEtag);
        }
        checkIfMatch(req, currentEtag);
    }

    /**
     * Honour an {@code If-Match} the client chose to send, without demanding one.
     *
     * <p>This is the rule for every state-changing method the spec does <em>not</em> single
     * out. lws10-core mandates the 428 in exactly two places — unconditional PUT on an
     * ETagged resource, and a PUT or PATCH on a linkset — and says of DELETE only that "on
     * success, the server MUST respond with 204 No Content. Servers <strong>SHOULD</strong>
     * support conditional requests". SHOULD <em>support</em> is an obligation to honour a
     * validator that arrives, not a licence to require one: reading it as a requirement
     * turns the 204 that MUST follow a successful delete into a 428 that never can.
     *
     * <p>So a client that sends a stale tag is still refused with 412, and one that sends
     * none still gets its delete. The optimistic-concurrency guarantee is unchanged for
     * everyone who asked for it, because it was only ever the client's to ask for.
     *
     * @param currentEtag the tag as read <em>inside the write transaction</em>
     */
    public static void checkIfMatch(HttpServletRequest req, String currentEtag) {
        String ifMatch = req.getHeader("If-Match");
        if (ifMatch == null || ifMatch.isBlank()) {
            return;
        }
        if ("*".equals(ifMatch.trim())) {
            // Satisfied by the resource merely existing, which the caller established.
            return;
        }
        if (!matches(ifMatch, currentEtag)) {
            throw Problem.preconditionFailed("the resource has changed since it was read")
                    .header("ETag", currentEtag);
        }
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
            return "*".equals(inm.trim()) || matches(inm, etag);
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

    /** Does a comma-separated list of entity tags contain this one? */
    private static boolean matches(String header, String etag) {
        if (etag == null) {
            return false;
        }
        for (String candidate : header.split(",")) {
            String c = candidate.trim();
            // A weak validator compares equal to its strong counterpart here; we only
            // ever mint strong tags, so just strip the marker.
            if (c.startsWith("W/")) {
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
