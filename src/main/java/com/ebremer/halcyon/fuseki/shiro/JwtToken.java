package com.ebremer.halcyon.fuseki.shiro;


import com.ebremer.halcyon.datum.HalcyonPrincipal;
import io.jsonwebtoken.Claims;
import java.security.PublicKey;
import org.apache.shiro.authc.AuthenticationToken;

public class JwtToken implements AuthenticationToken {

    private final String token;
    private final Claims claims;
    private final HalcyonPrincipal principal;

    public JwtToken(String token) {
        this.token = token;
        this.principal = new HalcyonPrincipal(this, false);
        PublicKey publicKey = KeycloakPublicKeyFetcher.getKeycloakPublicKeyFetcher().getPublicKey();
        this.claims = new JwtVerifier(publicKey).verify(token);
    }

    /*
    @Override
    public Object getPrincipal() {
        return token; //getClaims().getSubject();
    }*/

    @Override
    public HalcyonPrincipal getPrincipal() {
        return principal;
    }
    
    /*
    public HalcyonPrincipal getHalcyonPrincipal() {
        return principal;
    }*/
    
    public String getJwt() {
        return token;
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    public Claims getClaims() {
        return claims;
    }
}
