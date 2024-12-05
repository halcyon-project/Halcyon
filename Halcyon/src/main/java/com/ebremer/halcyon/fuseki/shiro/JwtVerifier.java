package com.ebremer.halcyon.fuseki.shiro;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import java.security.PublicKey;
import java.util.logging.Logger;
import java.util.logging.Level;

public class JwtVerifier {

    private final PublicKey publicKey;
    private static final Logger logger = Logger.getLogger(JwtVerifier.class.getName());

    public JwtVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public Claims verify(String token) {
        try {         
            JwtParser parser = Jwts.parser()
                    .clockSkewSeconds(30) // Allow some clock skew
                    .verifyWith(publicKey)
                    .build();
            Jws<Claims> claimsJws = parser.parseClaimsJws(token);
            return claimsJws.getBody();
        } catch (io.jsonwebtoken.ExpiredJwtException ex) {
            logger.log(Level.SEVERE, "JWT expired: {0}", ex.getMessage());
        } catch (io.jsonwebtoken.security.SignatureException ex) {
            logger.log(Level.SEVERE, "Invalid signature: {0}", ex.getMessage());
        }
        //return new DefaultClaims();
        return null;
    }
}
