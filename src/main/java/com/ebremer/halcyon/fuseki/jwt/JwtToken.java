package com.ebremer.halcyon.fuseki.jwt;

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
        return getClaims().getSubject();
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    public Claims getClaims() {
        return claims;
    }
}

