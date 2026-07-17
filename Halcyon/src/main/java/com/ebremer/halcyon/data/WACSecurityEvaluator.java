package com.ebremer.halcyon.data;

import com.ebremer.halcyon.data.DataCore.Level;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import static com.ebremer.halcyon.data.DataCore.Level.OPEN;
import com.ebremer.halcyon.fuseki.shiro.JwtToken;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.pools.AccessCache;
import com.ebremer.halcyon.pools.AccessCachePool;
import com.ebremer.ns.HAL;
import com.ebremer.ns.LWS;
import com.ebremer.ns.WAC;
import java.security.Principal;
import java.util.Set;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;

/**
 * 
 * @author erich
 */
public final class WACSecurityEvaluator implements SecurityEvaluator {
    private final Level level;
    
    public WACSecurityEvaluator(Level level) {
        this.level = level;
    }

    @Override
    public boolean evaluate(Object principal, Action action, Node node) {
        if (node.equals(Node.ANY)) {
            return false;
        }
        // M1: resolve the WAC mode for this action up front. An action that
        // cannot be mapped to a mode is a HARD DENY — we must never bind ?mode
        // to null (which previously left the write-authz ASK either NPE'ing or
        // matching any rule). Read -> acl:Read; Create/Update/Delete -> acl:Write.
        String mode = WACUtil.WAC(action);
        if (mode == null) {
            return false;
        }
        // M5 — this pre-grant is an ENVELOPE grant, not a blanket disclosure, and
        // it is load-bearing. It only ever fires for the CollectionsAndResources
        // GRAPH node itself. Its two callers (the ListImages/ListFeatures
        // collection dropdowns, via getSecuredDataset(OPEN) -> Patterns
        // .getCollectionRDF2) issue "GRAPH <CollectionsAndResources> {...}" with
        // the graph bound to a CONSTANT, which ARQ 6.1.0 routes through
        // SecuredDatasetGraph.getGraph(node) -> Factory.getInstance(...), i.e. a
        // jena-permissions SECURED graph. Every triple inside is then re-checked
        // by evaluate(principal, action, graphIRI, triple), which authorizes the
        // triple's SUBJECT (a urn:uuid: container) under the normal per-agent
        // ACL — the branch below cannot match a container subject, so per-tenant
        // filtering still happens. Dropping this grant does NOT tighten anything:
        // it just empties both dropdowns, because no rule grants acl:Read on the
        // CollectionsAndResources graph IRI (rules target urn:uuid: containers).
        //
        // INVARIANT this depends on: the catalog is only ever read via a CONSTANT
        // GRAPH pattern. SecuredDatasetGraph.find/findNG hand back base.find*()
        // RAW once hasReadAccess(g) passes, so an OPEN-level query shaped
        // "GRAPH ?g {...}" (variable) would bypass the per-triple filter and
        // expose the whole catalog. Keep catalog reads on a constant graph.
        //
        // M1 additionally restricts this to Read, so OPEN can never authorize a
        // mutation of the catalog.
        if (level == OPEN && action == Action.Read) {
            if (node.equals(HAL.CollectionsAndResources.asNode())) {
                return true;
            }
        }
        HalcyonPrincipal hp = (HalcyonPrincipal) principal;
        AccessCache ac;
        try {
            ac = AccessCachePool.getPool().borrowObject(hp.getUserURI());
        } catch (Exception ex) {
            return false;
        }
        // H5: a hit is only honoured while it is still fresh — AccessCache.lookup
        // expires it past the per-decision TTL, and the pool has already
        // refreshed this cache if the SECM was rebuilt since it snapshotted.
        Boolean cached = ac.lookup(node, action);
        if (cached != null) {
            AccessCachePool.getPool().returnObject(hp.getUserURI(), ac);
            return cached;
        }
        // H6 — the containment step walks (so:hasPart|lws:contains)*, not just
        // so:hasPart*. NOTHING in this system ever writes so:hasPart between a
        // container and its contents: the ingest path (DirectoryProcessor.PathInfo)
        // links them with lws:contains/lws:partOf and stores that in
        // CollectionsAndResources — which IS part of the SECM queried here, so the
        // triples were present all along, just under a predicate this ASK did not
        // look at. so:hasPart is only ever written between Keycloak GROUPS
        // (HalcyonSession.ParseLab).
        //
        // The consequence was that acl:accessTo/so:hasPart* could only ever match
        // the rule's own target (the zero-length path), so a Read/Write grant on a
        // container never reached the images inside it — every image was denied,
        // which is exactly why every listing still reads the RAW dataset with its
        // getSecuredDataset(...) line commented out. Inheritance now works, which
        // is what makes routing those listings through the secured dataset possible.
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            ASK {?rule acl:accessTo/(so:hasPart|lws:contains)* ?target;
                        acl:mode ?mode;
                        acl:agent ?group
            }
        """);
        pss.setNsPrefix("acl", WAC.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("lws", LWS.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("target", node.toString());
        pss.setIri("mode", mode);
        pss.setIri("group", HAL.Anonymous.toString());
        // H13: the SECM is an in-memory snapshot, so there is no transaction to
        // strand here — but this is the per-triple authorization path, the
        // highest-churn query in the system, so an unclosed execution per triple is
        // the one in-memory instance actually worth fixing.
        boolean anon;
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ac.getSECM())) {
            anon = qe.execAsk();
        }
        if (anon) {
            ac.record(node, action, true);
            AccessCachePool.getPool().returnObject(hp.getUserURI(), ac);
            return true;
        }
        pss = new ParameterizedSparqlString("""
            ASK {?rule acl:accessTo/(so:hasPart|lws:contains)* ?target;
                        acl:mode ?mode;
                        acl:agent ?group .
                ?group so:member ?member
            }
        """);
        pss.setNsPrefix("acl", WAC.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("lws", LWS.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("target", node.toString());
        pss.setIri("mode", mode);
        pss.setIri("member", hp.getUserURI());
        boolean ha;
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ac.getSECM())) {
            ha = qe.execAsk();
        }
        ac.record(node, action, ha);
        AccessCachePool.getPool().returnObject(hp.getUserURI(), ac);
        return ha;

    }

    @Override
    public boolean evaluate(Object principal, Action action, Node graphIRI, Triple triple) {
        //return evaluate( principal, triple );
        return evaluate( principal, action, triple.getSubject());
        //return evaluate( principal, action, triple.getSubject()) && evaluate( principal, action, triple.getObject()) && evaluate( principal, action, triple.getPredicate());
    }
    
    //private boolean evaluate( Object principal, Triple triple ) {
      //  return evaluate( principal, triple.getSubject()) && evaluate( principal, triple.getObject()) && evaluate( principal, triple.getPredicate());
    //}
    
    @Override
    public boolean evaluate(Object principal, Set<Action> actions, Node graphIRI) {
        return SecurityEvaluator.super.evaluate(principal, actions, graphIRI);
    }

    @Override
    public boolean evaluate(Object principal, Set<Action> actions, Node graphIRI, Triple triple) {
        return SecurityEvaluator.super.evaluate(principal, actions, graphIRI, triple);
    }
    
    /*
    private boolean evaluate( Object principal, Node node ) {
        if (node.equals(Node.ANY)) {
            return false; // all wild cards are false
        }
        return node.equals( Node.ANY );
    }*/


    @Override
    public boolean evaluateAny(Object principal, Set<Action> actions, Node graphIRI) {
        return SecurityEvaluator.super.evaluateAny(principal, actions, graphIRI);
    }

    @Override
    public boolean evaluateAny(Object principal, Set<Action> actions, Node graphIRI, Triple triple) {
        return SecurityEvaluator.super.evaluateAny(principal, actions, graphIRI, triple);
    }

    @Override
    public boolean evaluateUpdate(Object principal, Node graphIRI, Triple from, Triple to) {
        return SecurityEvaluator.super.evaluateUpdate(principal, graphIRI, from, to);
    }

    /**
     * L4: the cast used to be unguarded, and only
     * {@code UnavailableSecurityManagerException} was caught — so a Shiro subject
     * whose principal was anything other than a {@code JwtToken} threw
     * {@code ClassCastException}, and one with no principal at all threw NPE,
     * straight out of the evaluator that every WAC decision runs through. The
     * fall-through to the session principal was already the intended path for
     * the Keycloak servlet-filter case; an unexpected principal type now takes
     * it too instead of escaping as an unrelated exception.
     */
    @Override
    public Principal getPrincipal() {
        try {
            if (SecurityUtils.getSubject().getPrincipal() instanceof JwtToken jwt) {
                return jwt.getPrincipal();
            }
        } catch (UnavailableSecurityManagerException ex) {
            // assume and try for a Keycloak Servlet Filter Auth
        }
        return HalcyonSession.get().getHalcyonPrincipal();
    }

    @Override
    public boolean isPrincipalAuthenticated(Object o) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isHardReadError() {
        return SecurityEvaluator.super.isHardReadError();
    }
}
