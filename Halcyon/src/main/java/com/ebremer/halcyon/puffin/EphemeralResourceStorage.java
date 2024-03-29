package com.ebremer.halcyon.puffin;

import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.rdf.model.Resource;

/**
 *
 * @author erich
 */
public class EphemeralResourceStorage implements AutoCloseable {

    private final ConcurrentHashMap<String,Resource> map;

    public EphemeralResourceStorage() {
        map = new ConcurrentHashMap<>();
    }
    
    public void put(String key, Resource m) {
        map.put(key,m);
    }
    
    public synchronized Resource get(String key) {
        return map.get(key);
    }
    
    public synchronized void remove(String key) {
        map.remove(key);
        System.out.println("remain temp items "+map.size());
    }
    
    @Override
    public void close() {
        map.clear();
    }
}
