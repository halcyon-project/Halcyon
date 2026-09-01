package com.ebremer.lws.sparql;

import com.ebremer.lws.auth.oidc.SsrfGuard;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.jena.query.Query;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.OpVisitorBase;
import org.apache.jena.sparql.algebra.op.OpService;
import org.apache.jena.sparql.algebra.walker.Walker;
import org.apache.jena.sparql.expr.ExprVisitorBase;

/**
 * The egress policy for client-supplied SPARQL.
 *
 * <p>A {@code SERVICE} clause makes <em>this server</em> issue an HTTP request to an endpoint the
 * query author chose. Every entry point that runs a caller's query is therefore an outbound-request
 * primitive, and there are several: the store-wide {@code /rdf2} endpoint, the per-resource
 * BeakGraph query surface (every {@code .h5} resource URL), and the MCP {@code sparql_query} tool.
 * They need one rule between them, not three, which is what this class is.
 *
 * <p>Two policies, because the entry points genuinely differ:
 * <ul>
 *   <li>{@link #refuseFederation} — an outright ban, for callers that have no business federating
 *       at all. The MCP tool surface uses this.</li>
 *   <li>{@link #checkEgress} — federation is allowed, but every target is put through
 *       {@link SsrfGuard} first, so a query cannot reach loopback, private, link-local or
 *       cloud-metadata addresses. The HTTP endpoints use this, because self-federation is a
 *       deliberate feature here: every LWS resource is a SPARQL endpoint on this server's own
 *       origin, and {@code ServiceHttpClient} exists specifically so a {@code SERVICE} back to it
 *       completes. That is why this is not simply a ban — banning would delete a working feature
 *       to fix a different problem. The server's own host is passed in as an allowed host.</li>
 * </ul>
 *
 * <p><strong>Why the algebra and not the syntax tree.</strong> Walking the parsed
 * {@code Element} tree — even descending into subqueries — does not see a {@code SERVICE} nested
 * inside {@code FILTER EXISTS}, {@code FILTER NOT EXISTS} or {@code BIND(EXISTS{...})}, because
 * those hold their group pattern inside an <em>expression</em> that an
 * {@code ElementWalker} never enters. Measured against the previous syntax-tree check, all three
 * shapes went undetected while plain, subquery, {@code OPTIONAL}, {@code MINUS}, {@code UNION} and
 * {@code GRAPH} nesting were caught — so the ban read as working while being one keyword away from
 * bypass. Compiling to the algebra and walking it with an expression visitor puts every
 * {@code SERVICE} in one place, including the ones inside {@code ExprFunctionOp}.
 */
public final class SparqlGuard {

    private SparqlGuard() {
    }

    /** Thrown when a query may not be run as written. */
    public static final class RefusedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public RefusedException(String message) {
            super(message);
        }
    }

    /**
     * Every {@code SERVICE} endpoint the query names, in no particular order.
     *
     * <p>A {@code SERVICE ?var} — a variable endpoint, resolved per-binding at execution time —
     * cannot be known here and is reported as {@code null}, so callers can fail closed on it rather
     * than mistake "no targets found" for "no federation".
     */
    public static List<String> serviceTargets(Query query) {
        List<String> targets = new ArrayList<>();
        if (query == null) {
            return targets;
        }
        Walker.walk(Algebra.compile(query), new OpVisitorBase() {
            @Override
            public void visit(OpService op) {
                var node = op.getService();
                targets.add(node != null && node.isURI() ? node.getURI() : null);
            }
        }, new ExprVisitorBase());
        return targets;
    }

    /** Whether the query federates at all, however deeply the {@code SERVICE} is nested. */
    public static boolean federates(Query query) {
        return !serviceTargets(query).isEmpty();
    }

    /**
     * Refuse any federation whatsoever.
     *
     * @throws RefusedException if the query contains a {@code SERVICE} clause anywhere
     */
    public static void refuseFederation(Query query) {
        if (federates(query)) {
            throw new RefusedException(
                    "SERVICE is not accepted: a federated clause would make this server "
                    + "fetch caller-chosen URLs");
        }
    }

    /**
     * Allow federation, but only to targets that pass the SSRF policy.
     *
     * <p>{@code allowedHosts} opts specific host names past the internal-address rule — the
     * server's own host, and whatever the operator has listed in {@code lws-oidc.json}'s
     * {@code allowedInternalHosts}. It is deliberately the same allow-list the OIDC fetches use:
     * one egress policy for the process, not one per subsystem.
     *
     * @throws RefusedException if any target is a variable, or is refused by {@link SsrfGuard}
     */
    public static void checkEgress(Query query, Set<String> allowedHosts) {
        Set<String> hosts = allowedHosts == null ? Set.of() : allowedHosts;
        Set<String> seen = new LinkedHashSet<>();
        for (String target : serviceTargets(query)) {
            if (target == null) {
                // SERVICE ?endpoint: the destination is only known once bindings flow, which is
                // after the point where it could be refused. Fail closed.
                throw new RefusedException(
                        "SERVICE with a variable endpoint is not accepted: its target cannot be "
                        + "checked before the request is made");
            }
            if (!seen.add(target)) {
                continue;
            }
            try {
                SsrfGuard.verify(target, hosts);
            } catch (SsrfGuard.BlockedException e) {
                // The target came from the caller, so naming it back is not a disclosure -- and
                // without it the refusal is unactionable for a legitimate federated query.
                throw new RefusedException("SERVICE target refused: " + e.getMessage());
            }
        }
    }
}
