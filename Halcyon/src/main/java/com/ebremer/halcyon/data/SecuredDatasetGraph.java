package com.ebremer.halcyon.data;

import com.ebremer.halcyon.datum.AccessDeniedException;
import com.ebremer.halcyon.datum.HalSec;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.ns.HAL;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import static java.util.stream.Collectors.toList;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.permissions.Factory;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.TxnType;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.shared.Lock;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.graph.GraphZero;
import org.apache.jena.sparql.util.Context;

/**
 *
 * @author erich
 */
public class SecuredDatasetGraph implements DatasetGraph {
    private final DatasetGraph base;
    private final WACSecurityEvaluator securityEvaluator;

    public SecuredDatasetGraph(DatasetGraph base, WACSecurityEvaluator evaluator) {
        this.base = base;
        this.securityEvaluator = evaluator;
    }
    
    @Override
    public Iterator<Node> listGraphNodes() {
        List<Node> result = getBaseGraphNodes();
        return getReadAllowedGraphNodes(result).iterator();
    }
    
    private List<Node> getBaseGraphNodes() {
        List<Node> result = new LinkedList<>();
        base.listGraphNodes().forEachRemaining(result::add);
        return result;
    }
    
    private List<Node> getReadAllowedGraphNodes(List<Node> result) {
        return result.stream().filter(this::hasReadAccess).collect(toList());
    }
    
    private boolean hasReadAccess(Node node) {
        return securityEvaluator.evaluate(securityEvaluator.getPrincipal(), SecurityEvaluator.Action.Read, node);
    }

    @Override
    public Graph getDefaultGraph() {
        boolean isReadAllowed = false;
        if (isReadAllowed) {
            return base.getDefaultGraph();
        } else {
            return GraphZero.instance();
        }
    }

    @Override
    public Graph getGraph(Node graphNode) {
        boolean isReadAllowed = hasReadAccess(graphNode);
        if (isReadAllowed) {
            return Factory.getInstance(securityEvaluator, graphNode.getURI(), base.getGraph(graphNode));
        } else {
            return (Graph) GraphZero.instance();
        }
    }

    @Override
    public Graph getUnionGraph() {
        Graph g = base.getUnionGraph();
        return Factory.getInstance(securityEvaluator, "http://localhost/unionx", g);
    }

    @Override
    public boolean containsGraph(Node graphNode) {
        return hasReadAccess(graphNode);
    }

    @Override
    public void addGraph(Node node, Graph graph) {
        HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
        if (HalSec.canCreateCollection(hp.getUserURI())) {
            base.addGraph(node, graph);
        } else {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private boolean hasDeleteAccess(Node test) {
        return securityEvaluator.evaluate(securityEvaluator.getPrincipal(), SecurityEvaluator.Action.Delete, test);
    }
    
    @Override
    public void removeGraph(Node node) {
        if (hasDeleteAccess(node)) {
            base.removeGraph(node);
        } else {
            throw new AccessDeniedException("User is not allowed to delete graph " + node);
        }
    }
    
    private boolean hasCreateAccess(Node test) {
        return securityEvaluator.evaluate(securityEvaluator.getPrincipal(), SecurityEvaluator.Action.Create, test);
    }

    @Override
    public void add(Quad quad) {
        Node graph = quad.getGraph();
        if (hasCreateAccess(graph)) {
            base.add(quad);
        } else {
            throw new AccessDeniedException("User is not allowed to add triples to graph " + graph);
        }
    }

    @Override
    public void delete(Quad quad) {
        Node graph = quad.getGraph();
        if (hasDeleteAccess(graph)) {
            base.delete(quad);
        } else {
            throw new AccessDeniedException("User is not allowed to delete triples from graph " + graph);
        }
    }

    @Override
    public void delete(Node g, Node s, Node p, Node o) {
        if (hasDeleteAccess(g)) {
            base.delete(g, s, p, o);
        } else {
            throw new AccessDeniedException("User is not allowed to delete triples from graph " + g);
        }
    }

    @Override
    public void deleteAny(Node node, Node node1, Node node2, Node node3) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Iterator<Quad> find() {
        return Iter.filter(base.find(), quad -> hasReadAccess(quad.getGraph()));
    }

    @Override
    public Iterator<Quad> find(Quad quad) {
        return find(quad.getGraph(), quad.getSubject(), quad.getPredicate(), quad.getObject());
    }

    @Override
    public Iterator<Quad> find(Node g, Node s, Node p, Node o) {
        if (g == null || Quad.isDefaultGraph(g)) { // default graph case
            if (hasReadAccess(NodeFactory.createURI(HAL.SecurityGraph.getURI()))) {
                return base.find(g, s, p, o);
            }
        } else if (g.equals(Node.ANY)) {
            return Iter.filter(base.find(g, s, p, o), q -> hasReadAccess(q.getGraph()));
        } else {
            if (hasReadAccess(g)) {
                return base.find(g, s, p, o);
            }
        }
        throw new AccessDeniedException("User is not allowed to read triples from graph " + g);
    }

    @Override
    public Iterator<Quad> findNG(Node g, Node s, Node p, Node o) {
        if (g == null || Quad.isDefaultGraph(g)) { // default graph case
            if (hasReadAccess(NodeFactory.createURI(HAL.SecurityGraph.getURI()))) {
                return base.findNG(g, s, p, o);
            }
        } else if (g.equals(Node.ANY)) {
            return Iter.filter(base.findNG(g, s, p, o), q -> hasReadAccess(q.getGraph()));
        } else {
            if (hasReadAccess(g)) {
                return base.findNG(g, s, p, o);
            }
        }
        throw new AccessDeniedException("User is not allowed to read triples from graph " + g);
    }

    @Override
    public boolean contains(Node g, Node s, Node p, Node o) {
        Iterator<Quad> iter = find(g, s, p, o);
        boolean b = iter.hasNext();
        Iter.close(iter);
        return b;
    }

    @Override
    public boolean contains(Quad quad) {
        return contains(quad.getGraph(), quad.getSubject(), quad.getPredicate(), quad.getObject());
    }

    @Override
    public void clear() {
        if (getBaseGraphNodes().stream().allMatch(this::hasDeleteAccess)) {
            base.clear();
        }
        throw new AccessDeniedException("User is not allowed to clear dataset.");
    }

    @Override
    public boolean isEmpty() {
        return !contains(Node.ANY, Node.ANY, Node.ANY, Node.ANY);
    }

    @Override
    public Lock getLock() {
        return base.getLock();
    }

    @Override
    public Context getContext() {
        return base.getContext();
    }

    @Override
    public long size() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void close() {
        base.close();
    }

    @Override
    public PrefixMap prefixes() {
        return base.prefixes();
    }

    @Override
    public boolean supportsTransactions() {
        return base.supportsTransactions();
    }
    
    @Override
    public void begin(TxnType type) {
        base.begin();
    }

    @Override
    public void begin(ReadWrite readWrite) {
        base.begin(readWrite);
    }

    @Override
    public boolean promote(Promote mode) {
        return base.promote(mode);
    }

    @Override
    public void commit() {
        base.commit();
    }

    @Override
    public void abort() {
        base.abort();
    }

    @Override
    public void end() {
        base.end();
    }

    @Override
    public ReadWrite transactionMode() {
        return base.transactionMode();
    }

    @Override
    public TxnType transactionType() {
        return base.transactionType();
    }

    @Override
    public boolean isInTransaction() {
        return base.isInTransaction();
    }   

    @Override
    public void add(Node g, Node s, Node p, Node o) {
        if (hasCreateAccess(g)) {
            base.add(g, s, p, o);
        } else {
            throw new AccessDeniedException("User is not allowed to add triples to graph " + g);
        }
    }
}
