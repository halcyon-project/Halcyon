package com.ebremer.halcyon.server;

import com.ebremer.lws.auth.oidc.SsrfGuard;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.algebra.op.OpService;
import org.apache.jena.sparql.engine.ExecutionContext;
import org.apache.jena.sparql.engine.QueryIterator;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.service.ServiceExecutorRegistry;
import org.apache.jena.sparql.service.single.ChainingServiceExecutor;
import org.apache.jena.sparql.service.single.ServiceExecutor;
import org.apache.jena.sparql.util.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the egress policy to a {@code SERVICE} whose endpoint is a variable.
 *
 * <p>{@link SparqlGuard} can check a constant {@code SERVICE <uri>} before the query runs, and does.
 * A variable endpoint — {@code SERVICE ?g} — has no target until bindings flow, so there is nothing
 * to inspect up front. Refusing those outright was the obvious safe answer and the wrong one: this
 * server is built so that <em>every LWS resource is itself a SPARQL endpoint</em>, which makes
 *
 * <pre>
 * select ?g where { graph ?g { ?s as:mediaType "application/x-hdf5" }
 *                   service ?g { ?sx exif:width ?w } }
 * </pre>
 *
 * the natural way to ask a question of every matching resource at once. Blanket refusal deleted
 * that capability to close a hole that can be closed properly.
 *
 * <p>Jena's service executor chain is where it belongs. The second {@link OpService} handed to
 * {@link #createExecution} is the <em>substituted</em> one, with the variable already resolved
 * against the current binding, so the real destination is known here — immediately before the
 * request is made, and once per resolved target rather than once per query. That is strictly
 * better than a static check: it also catches an endpoint that a constant-looking query derives
 * through {@code VALUES} or a sub-select.
 *
 * <p>The allow-list is {@link SparqlEgress}'s, so a variable endpoint resolving to this server's own
 * origin is permitted exactly as a constant one is, and one resolving to a loopback, private,
 * link-local or cloud-metadata address is refused exactly as a constant one is.
 */
public final class SparqlServiceEgressExecutor implements ChainingServiceExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(SparqlServiceEgressExecutor.class);

    private static final SparqlServiceEgressExecutor INSTANCE = new SparqlServiceEgressExecutor();

    private SparqlServiceEgressExecutor() {
    }

    /**
     * Register the check on the global service executor chain.
     *
     * <p>Must be called at startup, beside {@link ServiceHttpClient#install()}: without it a
     * variable endpoint is unchecked, and {@link SparqlGuard} deliberately lets those through on
     * the understanding that this is here to catch them.
     */
    public static void install() {
        ServiceExecutorRegistry.get().addSingleLink(INSTANCE);
        LOG.info("SPARQL SERVICE egress check installed for variable endpoints");
    }

    @Override
    public QueryIterator createExecution(OpService original, OpService substituted, Binding binding,
            ExecutionContext execCxt, ServiceExecutor chain) {
        Node target = substituted == null ? null : substituted.getService();
        if (target != null && target.isURI()) {
            try {
                SsrfGuard.verify(target.getURI(), SparqlEgress.allowedHosts());
            } catch (SsrfGuard.BlockedException e) {
                // The caller chose the pattern that produced this target, so naming it back is not
                // a disclosure, and without it a federated query that partly resolves inside the
                // network is impossible to debug.
                throw new org.apache.jena.query.QueryExecException(
                        "SERVICE target refused: " + e.getMessage());
            }
        }
        return chain.createExecution(original, substituted, binding, execCxt);
    }

    /** Visible for tests: the check as a predicate, without an execution to chain to. */
    static void check(String uri) {
        SsrfGuard.verify(uri, SparqlEgress.allowedHosts());
    }

    /** Visible for tests. */
    static Context noContext() {
        return null;
    }
}
