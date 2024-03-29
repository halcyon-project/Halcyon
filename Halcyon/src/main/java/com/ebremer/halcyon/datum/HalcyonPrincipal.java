package com.ebremer.halcyon.datum;

import com.ebremer.halcyon.fuseki.shiro.JwtToken;
import com.ebremer.halcyon.fuseki.shiro.JwtVerifier;
import com.ebremer.halcyon.fuseki.shiro.KeycloakPublicKeyFetcher;
import com.ebremer.ns.HAL;
import io.jsonwebtoken.Claims;
import java.io.Serializable;
import java.security.Principal;
import java.security.PublicKey;
import java.util.ArrayList;

/**
 *
 * @author erich
 */
public class HalcyonPrincipal implements Principal, Serializable {
    private final String uuid;
    private String webid;
    private final boolean anonymous;
    private String name = "Anonymous User";
    private String token;
    private String lastname;
    private String firstname;
    private ArrayList<String> groups;
 
    public HalcyonPrincipal(String uuid, boolean anonymous) {
        this.uuid = uuid;
        this.anonymous = anonymous;
        groups = new ArrayList<>();
        groups.add(HAL.Anonymous.toString());
    }
    
    public HalcyonPrincipal(JwtToken jwttoken, boolean anonymous) {
        this.token = (String) jwttoken.getCredentials();
        Claims claims = getClaims(token);
        uuid = "urn:uuid:"+claims.get("sub");
        //System.out.println("AUUID --> "+uuid);
        this.anonymous = anonymous;
        /*
        claims.keySet().forEach(f->{
            System.out.println("CLAIM : "+f+" ===> "+claims.get(f));
        });
*/
        if (claims.keySet().contains("family_name")) {
            lastname = (String) claims.get("family_name");
        } else {
            lastname = "";
        }
        if (claims.keySet().contains("given_name")) {
            firstname = (String) claims.get("given_name");
        } else {
            firstname = "";
        }
        if (claims.keySet().contains("HalcyonGroups")) {
            groups = (ArrayList) claims.get("HalcyonGroups");
        } else {
            firstname = "";
        }
        if (!anonymous) {
            name = firstname+" "+lastname;
        }
    }
    
    private Claims getClaims(String tokenx) {
        PublicKey publicKey = KeycloakPublicKeyFetcher.getKeycloakPublicKeyFetcher().getPublicKey();
        Claims claimsx = null;
        try {
            claimsx = new JwtVerifier(publicKey).verify(tokenx);
        } catch (Exception ex) {
            System.out.println(ex.toString());
        }
        return claimsx;
    }

    public String getURNUUID() {
        return uuid;
    }
    
    public String getToken() {
        return token;
    }
    
    public boolean isAnon() {
        return anonymous;
    }
    
    public String getWebID() {
        return webid;
    }
    
    public ArrayList getGroups() {
        return groups;
    }

    @Override
    public String getName() {
        return name;
    }
}
