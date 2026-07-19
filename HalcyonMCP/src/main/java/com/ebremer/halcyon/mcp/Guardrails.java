package com.ebremer.halcyon.mcp;

import java.time.Duration;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryException;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.sparql.syntax.ElementService;
import org.apache.jena.sparql.syntax.ElementSubQuery;
import org.apache.jena.sparql.syntax.ElementVisitorBase;
import org.apache.jena.sparql.syntax.ElementWalker;

/**
 * MCP-5: bounded by construction. Every reading tool this module ever grows
 * routes its limits through here — one place to see the caps, one place a
 * review has to check. The bar is the C2 lesson ({@code Main.java}): the old
 * Raptor servlet died for running raw query parameters unauthenticated,
 * unbounded, against a path-selected graph. Nothing in this module may
 * recreate it, and these helpers are how tools avoid recreating it by
 * accident.
 *
 * <ul>
 *   <li>{@link #MAX_TEXT_BYTES} — the text-read cap, the same 256 kB the
 *       preview relay enforces ({@code LwsClient.preview} aborts the transfer
 *       at the cap; a tool passes this constant, never its own number).</li>
 *   <li>{@link #MAX_ROWS} / {@link #QUERY_TIMEOUT} — the SPARQL result and
 *       wall-clock budgets MCP-9 must apply at execution.</li>
 *   <li>{@link #readOnlyQuery} — the only sanctioned way to accept SPARQL
 *       text from a caller (see there).</li>
 * </ul>
 */
public final class Guardrails {

    private Guardrails() {
    }

    /** Text-read cap — matches the preview relay's 256 kB discipline. */
    public static final int MAX_TEXT_BYTES = 256 * 1024;

    /**
     * Cap on an image a tool base64-inlines into a model's context. Thumbnails
     * are meant to be small; a response over this is refused rather than
     * flooding the context (and the dimension is capped besides).
     */
    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    /** Largest thumbnail edge a tool will request from the IIIF service. */
    public static final int MAX_IMAGE_EDGE = 1024;

    /** Hard ceiling on rows any SPARQL tool may return. */
    public static final long MAX_ROWS = 1000;

    /** Wall-clock budget for one SPARQL execution. */
    public static final Duration QUERY_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Parse caller-supplied SPARQL as a bounded, read-only query — or refuse.
     *
     * <p>Three properties hold on every return, by construction:
     * <ul>
     *   <li><strong>Read-only:</strong> the update grammar is a different
     *       parser entirely, so {@code INSERT}/{@code DELETE}/{@code DROP}/
     *       {@code LOAD} do not parse here at all — there is no flag to
     *       forget.</li>
     *   <li><strong>No federation:</strong> a {@code SERVICE} clause anywhere
     *       (subqueries included) is refused — it would let a caller aim this
     *       server's network position at arbitrary URLs (SSRF), and no P1
     *       tool has a reason to federate.</li>
     *   <li><strong>Bounded:</strong> a missing {@code LIMIT} becomes
     *       {@code maxRows}; a larger one is clamped down; a smaller one is
     *       kept. ({@code ASK} has no rows to bound.)</li>
     * </ul>
     *
     * <p>The wall-clock half of the budget cannot live in the query object —
     * MCP-9 applies {@link #QUERY_TIMEOUT} on the execution it builds, and
     * runs it against the SECURED dataset only.
     *
     * @throws IllegalArgumentException when the text is not a valid read-only
     *                                  query, or uses {@code SERVICE}
     */
    public static Query readOnlyQuery(String sparql, long maxRows) {
        Query q;
        try {
            q = QueryFactory.create(sparql);
        } catch (QueryException e) {
            throw new IllegalArgumentException(
                    "not a valid SPARQL query (updates are not accepted): " + e.getMessage(), e);
        }
        if (containsService(q)) {
            throw new IllegalArgumentException(
                    "SERVICE is not accepted: a federated clause would make this server "
                    + "fetch caller-chosen URLs");
        }
        if (!q.isAskType()) {
            long limit = q.getLimit();
            if (limit == Query.NOLIMIT || limit > maxRows) {
                q.setLimit(maxRows);
            }
        }
        return q;
    }

    /** {@code SERVICE} anywhere in the pattern, descending into subqueries. */
    private static boolean containsService(Query q) {
        if (q.getQueryPattern() == null) {
            return false;
        }
        boolean[] found = new boolean[1];
        ElementWalker.walk(q.getQueryPattern(), new ElementVisitorBase() {
            @Override
            public void visit(ElementService el) {
                found[0] = true;
            }

            @Override
            public void visit(ElementSubQuery el) {
                if (el.getQuery().getQueryPattern() != null) {
                    ElementWalker.walk(el.getQuery().getQueryPattern(), this);
                }
            }
        });
        return found[0];
    }
}
