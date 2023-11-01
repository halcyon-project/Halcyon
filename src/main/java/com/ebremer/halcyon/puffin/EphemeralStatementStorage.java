package com.ebremer.halcyon.puffin;

import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.rdf.model.Statement;

/**
 *
 * @author erich
 */
public class EphemeralStatementStorage implements AutoCloseable {

    private final ConcurrentHashMap<String,Statement> map;
    
    public EphemeralStatementStorage() {
        map = new ConcurrentHashMap<>();
    }
    
    public void put(String key, Statement r) {
        map.put(key,r);
    }
    
    public Statement get(String key) {
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
