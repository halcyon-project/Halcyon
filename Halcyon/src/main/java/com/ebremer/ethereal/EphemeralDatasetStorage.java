package com.ebremer.ethereal;

import java.util.HashMap;
import org.apache.jena.query.Dataset;

/**
 *
 * @author erich
 */
public class EphemeralDatasetStorage {
    private final HashMap<String,Dataset> map;
    private static EphemeralDatasetStorage ems = null;
    
    private EphemeralDatasetStorage() {
        map = new HashMap<>();
    }
    
    public static EphemeralDatasetStorage getInstance() {
        if (ems==null) {
            ems = new EphemeralDatasetStorage();
        }
        return ems;
    }
    
    public synchronized void put(String key, Dataset m) {
        map.put(key,m);
    }
    
    public synchronized Dataset get(String key) {
        return map.get(key);
    }
    
    public synchronized void remove(String key) {
        map.remove(key);
        System.out.println("remain temp items "+map.size());
    }
}
