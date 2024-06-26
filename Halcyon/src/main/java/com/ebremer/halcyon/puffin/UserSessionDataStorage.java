package com.ebremer.halcyon.puffin;

import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class UserSessionDataStorage {
    private static final Logger logger = LoggerFactory.getLogger(UserSessionDataStorage.class);

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
        logger.debug("remove "+key);
        int c = map.size();
        if (map.containsKey(key)) {
            Block block = map.remove(key);
            block.close();
        } else {
            logger.debug("key "+key+" not present");
        }
        System.out.println("# of session blocks "+c+" --> "+map.size());
    }   
}
