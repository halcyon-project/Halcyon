package com.ebremer.halcyon.datum;

import io.jsonwebtoken.Claims;
import java.io.Serializable;
import java.security.Principal;

/**
 *
 * @author erich
 */
public class HalcyonPrincipal implements Principal, Serializable {
    private final String uuid;
    private String webid;
    private boolean anonymous;
    
    public HalcyonPrincipal(String uuid, boolean anonymous) {
        this.uuid = uuid;
        this.anonymous = anonymous;
    }
    
    public HalcyonPrincipal(Claims claims) {
        uuid = "urn:uuid:"+claims.getId();
        this.anonymous = false;
    }

    public String getURNUUID() {
        return uuid;
    }
    
    public boolean isAnon() {
        return anonymous;
    }
    
    public void setAnonymous(boolean anonymous) {
        this.anonymous = anonymous;
    }
    
    public String getWebID2() {
        return webid;
    }

    @Override
    public String getName() {
        return "Erich Bremer";
    }
}
