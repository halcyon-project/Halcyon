package com.ebremer.halcyon.puffin;

import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author erich
 */
public class UserSessionDataStorage {

    private final ConcurrentHashMap<String,Block> map;
    private static UserSessionDataStorage ems = null;
    
    private UserSessionDataStorage() {
        map = new ConcurrentHashMap<>();
    }
    
    public static UserSessionDataStorage getInstance() {
        if (ems==null) {
            ems = new UserSessionDataStorage();
        }
        return ems;
    }
    
    public void put(String key, Block r) {
        map.put(key,r);
    }
    
    public Block get(String key) {
        return map.get(key);
    }
    
    public void remove(String key) {
        int c = map.size();
        Block block = map.remove(key);
        block.close();
        System.out.println("# of session blocks "+c+" --> "+map.size());
    }   
}
