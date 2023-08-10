package com.ebremer.halcyon.puffin;

import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.rdf.model.Statement;

/**
 *
 * @author erich
 */
public class EphemeralStatementStorage {

    private final ConcurrentHashMap<String,Statement> map;
    private static EphemeralStatementStorage ems = null;
    
    private EphemeralStatementStorage() {
        map = new ConcurrentHashMap<>();
    }
    
    public static EphemeralStatementStorage getInstance() {
        if (ems==null) {
            ems = new EphemeralStatementStorage();
        }
        return ems;
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
}
