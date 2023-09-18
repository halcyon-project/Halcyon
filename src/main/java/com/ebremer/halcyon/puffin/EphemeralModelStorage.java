package com.ebremer.halcyon.puffin;

import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.rdf.model.Model;

/**
 *
 * @author erich
 */
public class EphemeralModelStorage implements AutoCloseable {

    private final ConcurrentHashMap<String,Model> map;

    public EphemeralModelStorage() {
        map = new ConcurrentHashMap<>();
    }
    
    public void put(String key, Model m) {
        map.put(key,m);
    }
    
    public synchronized Model get(String key) {
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
