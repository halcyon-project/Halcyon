package com.ebremer.lws.acp;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.vocab.LWSX;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.SecurityEvaluator;

/**
 * Bridges ACP to Halcyon's {@code jena-permissions} fork, so that queries over the
 * store are authorization-filtered by construction.
 *
 * <p>This is what makes the Type Index and Type Search services correct rather than
 * merely careful. Their spec is emphatic — unauthorized entries "MUST be omitted
 * entirely", a client "must not be able to discover the existence of a specific
 * resource instance, or that a specific type exists in the storage at all" — and the
 * reliable way to satisfy that is for the unauthorized data to be invisible to the
 * query engine, not for every call site to remember to filter afterwards.
 *
 * <p>It works because a resource's named graph <em>is</em> its URI, so the
 * {@code graphIRI} this receives is exactly the resource ACP reasons about.
 *
 * <p><strong>Reads only. Every write action is denied.</strong> Not an oversight —
 * the graph-level contract cannot express LWS's write rules. Creating a resource is
 * authorized against the <em>container</em>, but {@code evaluate(principal, Create,
 * graphIRI)} is handed the graph of the resource <em>being created</em>, which does
 * not exist yet and, in the flat storage, has no path back to its parent. Deleting a
 * resource likewise mutates its parent's {@code items}, so a delete driven through
 * this evaluator would be denied halfway through its own transaction. Mutations are
 * therefore authorized explicitly at the HTTP layer, against the right resource, and
 * then executed against the raw dataset. Returning {@code false} here makes any
 * attempt to write <em>through</em> this evaluator fail closed rather than
 * mysteriously half-succeed.
 *
 * <p><strong>One instance per request.</strong> {@code SecuredItemImpl} memoises
 * decisions in a {@code static ThreadLocal} whose cache key includes the evaluator
 * instance. A shared evaluator that read its principal from a thread-local or a
 * session would therefore collide keys across users on a pooled Jetty thread and
 * serve one user's ALLOW to another. The evaluator carries its agent, and is built
 * fresh for each request.
 */
public final class AcpSecurityEvaluator implements SecurityEvaluator {

    private final AgentContext agent;
    private final AcpEngine engine;

    public AcpSecurityEvaluator(AgentContext agent, AcpEngine engine) {
        this.agent = agent;
        this.engine = engine;
    }

    @Override
    public boolean evaluate(Object principal, Action action, Node graphIRI) {
        if (action != Action.Read) {
            return false;
        }
        if (graphIRI == null || !graphIRI.isURI()) {
            // Node.ANY, a variable, or the default graph. Never grant on a wildcard.
            return false;
        }
        String uri = graphIRI.getURI();

        // The internal graphs are not resources and are never readable through this
        // view. Exposing them would hand a client the ACLs and the storage keys.
        if (LWSX.SYSTEM_GRAPH.equals(uri)
                || LWSX.ACP_GRAPH.equals(uri)
                || LWSX.SUBSCRIPTION_GRAPH.equals(uri)) {
            return false;
        }
        return engine.allows(agent, uri, AccessMode.READ);
    }

    /**
     * A resource is the unit of authorization, and its graph holds exactly one
     * resource's metadata — so a triple grants nothing its graph does not. Note this
     * is <em>not</em> what Halcyon's {@code WACSecurityEvaluator} does: it evaluates
     * against {@code triple.getSubject()}, which under this model would authorize a
     * triple by the wrong resource entirely.
     */
    @Override
    public boolean evaluate(Object principal, Action action, Node graphIRI, Triple triple) {
        return evaluate(principal, action, graphIRI);
    }

    @Override
    public Object getPrincipal() {
        return agent;
    }

    @Override
    public boolean isPrincipalAuthenticated(Object principal) {
        return principal instanceof AgentContext a && a.isAuthenticated();
    }

    /**
     * False, so a denied read <em>filters</em> rather than throwing.
     *
     * <p>Exactly what the search services need: a resource the client may not see
     * should silently not appear, not raise an error that reveals it exists.
     */
    @Override
    public boolean isHardReadError() {
        return false;
    }
}
