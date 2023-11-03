package com.ebremer.halcyon.fuseki.shiro;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.security.PublicKey;

public class JwtVerifier {
    private final PublicKey publicKey;

    public JwtVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public Claims verify(String token) {
        try {
            JwtParser p = Jwts.parserBuilder()
                .setAllowedClockSkewSeconds(86400)
                .setSigningKey(publicKey)
                .build();
                Jws<Claims> claimx = p.parseClaimsJws(token);
            return claimx.getBody();
        } catch (io.jsonwebtoken.ExpiredJwtException ex) {
            System.out.println("ARRRGGGG JWT expired!!!!");
        }  catch (io.jsonwebtoken.security.SignatureException ex) {
            System.out.println(ex.getMessage());
        }
        //return new DefaultClaims();
        System.out.println("FAIL!!!!");
        return null;
    }
}
