package com.ebremer.halcyon.fuseki.shiro;


import io.jsonwebtoken.Claims;
import java.security.PublicKey;
import org.apache.shiro.authc.AuthenticationToken;

public class JwtToken implements AuthenticationToken {

    private final String token;
    private final Claims claims;

    public JwtToken(String token) {
        this.token = token;
        PublicKey publicKey = KeycloakPublicKeyFetcher.getKeycloakPublicKeyFetcher().getPublicKey();
        this.claims = new JwtVerifier(publicKey).verify(token);
    }

    @Override
    public Object getPrincipal() {
        return token; //getClaims().getSubject();
    }
    
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



/*
        this.token = token;
        PublicKey publicKey = KeycloakPublicKeyFetcher.getKeycloakPublicKeyFetcher().getPublicKey();
        Claims claimsx = null;
        try {
            claimsx = new JwtVerifier(publicKey).verify(token);
        } catch (Exception ex) {
            System.out.println(ex.toString());
        }
        this.claims = claimsx;
*/