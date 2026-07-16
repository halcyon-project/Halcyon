package com.ebremer.halcyon.fuseki.shiro;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import io.jsonwebtoken.Claims;
import org.apache.shiro.authc.AuthenticationToken;

public class JwtToken implements AuthenticationToken {

    private final String token;
    private final Claims claims;
    private final HalcyonPrincipal principal;

    /**
     * @throws IllegalArgumentException if the token does not verify — H2: this is
     *         now an explicit, fail-closed rejection. The principal used to be
     *         built FIRST and re-verified the token itself, so the only thing
     *         rejecting a forged token was an incidental NullPointerException
     *         deep inside HalcyonPrincipal when the claims came back null.
     */
    public JwtToken(String token) {
        this.token = token;
        // M4: the verifier selects the signing key by the token's own `kid`.
        // H2: it also enforces issuer / azp / required claims.
        this.claims = new JwtVerifier().verify(token);
        if (this.claims == null) {
            throw new IllegalArgumentException("JWT failed verification");
        }
        // Built only from claims that are already verified (H2).
        this.principal = new HalcyonPrincipal(this, false);
    }

    @Override
    public HalcyonPrincipal getPrincipal() {
        return principal;
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
