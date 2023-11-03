package com.ebremer.halcyon.pools;

import com.ebremer.halcyon.data.DataCore;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.graph.Node;
import org.apache.jena.permissions.SecurityEvaluator.Action;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 *
 * @author erich
 */
public class AccessCache {
    
    private final ConcurrentHashMap<Node,HashMap<Action,Boolean>> cache;
    private final Model secm;
    private final Model collections;
    
    public AccessCache() {
        cache = new ConcurrentHashMap<>();
        secm = ModelFactory.createDefaultModel();
        secm.add(DataCore.getInstance().getSECM());
        collections = ModelFactory.createDefaultModel();
    }
    
    public Model getSECM() {
        return secm;
    }

    public Model getCollections() {
        return collections;
    }
    
    public ConcurrentHashMap<Node,HashMap<Action,Boolean>> getCache() {
        return cache;
    }
}
