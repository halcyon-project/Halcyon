package com.ebremer.halcyon.data;

import com.ebremer.halcyon.data.DataCore.Level;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import static com.ebremer.halcyon.data.DataCore.Level.OPEN;
import com.ebremer.halcyon.fuseki.shiro.JwtToken;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.pools.AccessCache;
import com.ebremer.halcyon.pools.AccessCachePool;
import com.ebremer.ns.HAL;
import java.security.Principal;
import java.util.HashMap;
import java.util.Set;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.shared.AuthenticationRequiredException;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.WAC;
import org.apache.shiro.SecurityUtils;

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
    public boolean evaluate(Object principal, Action action, Node graphIRI) {
        //System.out.println(UUID.randomUUID().toString()+" evaluate(Object principal, Action action, Node graphIRI) --> "+action+"   "+graphIRI);
        if (level == OPEN) {
            if (graphIRI.matches(HAL.CollectionsAndResources.asNode())) {
                return true;
            }
        }
        HalcyonPrincipal hp = (HalcyonPrincipal) principal;
        AccessCache ac;
        try {
            ac = AccessCachePool.getPool().borrowObject(hp.getURNUUID());
        } catch (Exception ex) {
            return false;
        }
        if (ac.getCache().containsKey(graphIRI)) {
            if (ac.getCache().get(graphIRI).containsKey(action)) {
                //System.out.println("HIT "+graphIRI);
                boolean ha = ac.getCache().get(graphIRI).get(action);
                AccessCachePool.getPool().returnObject(hp.getURNUUID(), ac);
                return ha;
            }
        }
        //System.out.println("MISS "+graphIRI);
        HashMap<Action,Boolean> set = new HashMap<>();
        ac.getCache().put(graphIRI, set);
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            ASK {?rule acl:accessTo/so:hasPart* ?target;
                        acl:mode ?mode;
                        acl:agent ?group
            }
        """);
        pss.setNsPrefix("acl", WAC.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("target", graphIRI.toString());
        pss.setIri("mode", WACUtil.WAC(action));
        pss.setIri("group", HAL.Anonymous.toString());
        if (QueryExecutionFactory.create(pss.toString(), ac.getSECM()).execAsk()) {
            set.put(action, true);
            AccessCachePool.getPool().returnObject(hp.getURNUUID(), ac);
            return true;
        }
        pss = new ParameterizedSparqlString("""
            ASK {?rule acl:accessTo/so:hasPart* ?target;
                        acl:mode ?mode;
                        acl:agent ?group .
                ?group so:member ?member
            }
        """);
        pss.setNsPrefix("acl", WAC.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("target", graphIRI.toString());
        pss.setIri("mode", WACUtil.WAC(action));
        pss.setIri("member", hp.getURNUUID());
        boolean ha = QueryExecutionFactory.create(pss.toString(), ac.getSECM()).execAsk();
        set.put(action, ha);
        AccessCachePool.getPool().returnObject(hp.getURNUUID(), ac);
        return ha;
    }

    @Override
    public boolean evaluate(Object principal, Action action, Node graphIRI, Triple triple) {
        //System.out.println("evaluate(Object principal, Action action, Node graphIRI, Triple triple)");
        return evaluate( principal, triple );
    }
    
    private boolean evaluate( Object principal, Triple triple ) {
        //System.out.println("evaluate( Object principal, Triple triple )");
        return evaluate( principal, triple.getSubject()) && evaluate( principal, triple.getObject()) && evaluate( principal, triple.getPredicate());
    }
    
    private boolean evaluate( Object principal, Node node ) {
        return node.equals( Node.ANY );
    }

    @Override
    public boolean evaluate(Object principal, Set<Action> actions, Node graphIRI) throws AuthenticationRequiredException {
        return SecurityEvaluator.super.evaluate(principal, actions, graphIRI);
    }

    @Override
    public boolean evaluate(Object principal, Set<Action> actions, Node graphIRI, Triple triple) throws AuthenticationRequiredException {
        return SecurityEvaluator.super.evaluate(principal, actions, graphIRI, triple);
    }

    @Override
    public boolean evaluateAny(Object principal, Set<Action> actions, Node graphIRI) throws AuthenticationRequiredException {
        return SecurityEvaluator.super.evaluateAny(principal, actions, graphIRI);
    }

    @Override
    public boolean evaluateAny(Object principal, Set<Action> actions, Node graphIRI, Triple triple) throws AuthenticationRequiredException {
        return SecurityEvaluator.super.evaluateAny(principal, actions, graphIRI, triple);
    }

    @Override
    public boolean evaluateUpdate(Object principal, Node graphIRI, Triple from, Triple to) throws AuthenticationRequiredException {
        return SecurityEvaluator.super.evaluateUpdate(principal, graphIRI, from, to);
    }

    @Override
    public Principal getPrincipal() {
        try {
            return ((JwtToken) SecurityUtils.getSubject().getPrincipal()).getPrincipal();
        } catch (org.apache.shiro.UnavailableSecurityManagerException ex) {
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
