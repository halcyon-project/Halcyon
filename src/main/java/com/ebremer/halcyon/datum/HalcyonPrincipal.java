package com.ebremer.halcyon.datum;

import java.io.Serializable;
import java.security.Principal;

/**
 *
 * @author erich
 */
public class HalcyonPrincipal implements Principal, Serializable {
    private final String uuid;
    private String webid;
    
    public HalcyonPrincipal(String uuid) {
        System.out.println("CREATING HalcyonPrincipal : "+uuid);
        this.uuid = uuid;
    }

    public String getURNUUID() {
        return uuid;
    }   
    
    public String getWebID2() {
        return webid;
    }    

    @Override
    public String getName() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
