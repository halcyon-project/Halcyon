package com.ebremer.halcyon.puffin;

import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.query.QuerySolution;

/**
 *
 * @author erich
 */
public class EphemeralQuerySolutionStorage implements AutoCloseable {

    private final ConcurrentHashMap<String,QuerySolution> map;
    
    public EphemeralQuerySolutionStorage() {
        map = new ConcurrentHashMap<>();
    }
    
    public void put(String key, QuerySolution r) {
        map.put(key,r);
    }
    
    public QuerySolution get(String key) {
        return map.get(key);
    }
    
    public void remove(String key) {
        map.remove(key);
    }   

    @Override
    public void close() {
        map.clear();
    }
}
