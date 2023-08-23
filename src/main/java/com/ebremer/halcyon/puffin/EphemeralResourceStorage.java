package com.ebremer.halcyon.puffin;

import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.rdf.model.Resource;

/**
 *
 * @author erich
 */
public class EphemeralResourceStorage {

    private final ConcurrentHashMap<String,Resource> map;
    private static EphemeralResourceStorage ems = null;
    
    private EphemeralResourceStorage() {
        map = new ConcurrentHashMap<>();
    }
    
    public static EphemeralResourceStorage getInstance() {
        if (ems==null) {
            ems = new EphemeralResourceStorage();
        }
        return ems;
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
}
