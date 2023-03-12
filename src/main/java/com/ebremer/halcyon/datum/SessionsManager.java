package com.ebremer.halcyon.datum;

import com.ebremer.halcyon.keycloak.HALInMemorySessionIdMapper;
import org.keycloak.adapters.spi.SessionIdMapper;

/**
 *
 * @author erich
 */
public class SessionsManager {
    private static SessionsManager sm = null;
    private static SessionIdMapper idMapper = null;
    
    private SessionsManager() {
        
    }
    
    public SessionIdMapper getSessionIdMapper() {
        getSessionsManager();
        return idMapper;
    }
    
    public static SessionsManager getSessionsManager() {
        if (sm==null) {
            sm = new SessionsManager();
            idMapper = new HALInMemorySessionIdMapper();
        }
        return sm;
    }
}
