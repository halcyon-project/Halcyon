package com.ebremer.ethereal;

import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.rdf.model.Model;

/**
 *
 * @author erich
 */
public class EphemeralModelStorage {

    private final ConcurrentHashMap<String,Model> map;
    private static EphemeralModelStorage ems = null;
    
    private EphemeralModelStorage() {
        map = new ConcurrentHashMap<>();
    }
    
    public static EphemeralModelStorage getInstance() {
        if (ems==null) {
            ems = new EphemeralModelStorage();
        }
        return ems;
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
}
