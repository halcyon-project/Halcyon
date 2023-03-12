package com.ebremer.halcyon.fuseki.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import java.security.PublicKey;

public class JwtVerifier {
    private final PublicKey publicKey;

    public JwtVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public Claims verify(String token) {
        JwtParserBuilder b = Jwts.parserBuilder();
        Jws<Claims> claimx;
        try {
            claimx = b
                .setAllowedClockSkewSeconds(86400)
                .setSigningKey(publicKey)
                .build().parseClaimsJws(token);
            return claimx.getBody();
        } catch (io.jsonwebtoken.ExpiredJwtException ex) {
            System.out.println("ARRRGGGG JWT expired!!!!");
        }
        return null;
    }
}
