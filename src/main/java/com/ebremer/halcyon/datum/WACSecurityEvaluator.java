package com.ebremer.halcyon.datum;

import com.ebremer.ns.HAL;
import java.security.Principal;
import java.util.HashMap;
import java.util.Set;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.AuthenticationRequiredException;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.WAC;

/**
 * 
 * @author erich
 */
public class WACSecurityEvaluator implements SecurityEvaluator {
    private HalcyonPrincipal principal;
    private final Model secm;
    private final HashMap<Node,HashMap> cache;
    
    public WACSecurityEvaluator() {
        DataCore dc = DataCore.getInstance();
        cache = new HashMap<>();
        secm = ModelFactory.createDefaultModel();
        Dataset ds = dc.getDataset();
        ds.begin(ReadWrite.READ);
        secm.add(ds.getNamedModel(HAL.SecurityGraph.getURI()));
        secm.add(ds.getNamedModel(HAL.CollectionsAndResources.getURI()));
        secm.add(ds.getNamedModel(HAL.GroupsAndUsers.getURI()));
        ds.end();
    }
    
    public void setPrincipal( HalcyonPrincipal principal ) {
        this.principal = principal;
    }
    
    public Model getSecurityModel() {
        return secm;
    }

    @Override
    public boolean evaluate(Object principal, Action action, Node graphIRI) {
        return true;
        /*
        HalcyonPrincipal hp = (HalcyonPrincipal) principal;
        if (graphIRI.matches(HAL.CollectionsAndResources.asNode())) {
            return true;
        }
        if (cache.containsKey(graphIRI)) {
            if (cache.get(graphIRI).containsKey(action)) {
                //System.out.println("CACHE HIT!!!");
                return true;
            }
        }
        //System.out.println("CACHE MISS!!!");
        HashMap<Action,Boolean> set = new HashMap<>();
        cache.put(graphIRI, set);
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
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
        boolean ha = QueryExecutionFactory.create(pss.toString(), secm).execAsk();
        set.put(action, ha);
        return ha;*/
    }
    
    private boolean evaluate( Object principal, Triple triple ) {
        return evaluate( principal, triple.getSubject()) && evaluate( principal, triple.getObject()) && evaluate( principal, triple.getPredicate());
    }
    
    private boolean evaluate( Object principal, Node node ) {
        return node.equals( Node.ANY );
    }

    @Override
    public boolean evaluate(Object principal, Action action, Node graphIRI, Triple triple) {
        return !evaluate( principal, triple );
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
        return principal;
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
