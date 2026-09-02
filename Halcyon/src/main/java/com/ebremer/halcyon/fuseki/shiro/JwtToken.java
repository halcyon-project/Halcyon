package com.ebremer.halcyon.fuseki.shiro;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.server.WebIdLogin;
import io.jsonwebtoken.Claims;
import org.apache.shiro.authc.AuthenticationToken;

public class JwtToken implements AuthenticationToken {

    private final String token;
    /** The verified Keycloak claims, or {@code null} for a WebID (LWS-OIDC) credential. */
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
        Claims keycloakClaims = new JwtVerifier().verify(token);
        if (keycloakClaims != null) {
            // Built only from claims that are already verified (H2).
            this.claims = keycloakClaims;
            this.principal = new HalcyonPrincipal(this, false);
            return;
        }
        // Not a Keycloak token — try the LWS-OIDC (WebID login) credential, if enabled. Trust is
        // established dynamically from the WebID's CID, so /rdf accepts tokens from issuers it was
        // never configured with (the same LwsOidcVerifier /rdf2 uses); the store's WAC authorises
        // the WebID. The identity is the WebID, not Keycloak claims.
        String webId = WebIdBearer.verify(token);
        if (webId == null) {
            throw new IllegalArgumentException("JWT failed verification");
        }
        this.claims = null;
        this.principal = new HalcyonPrincipal(webId, WebIdLogin.groupsFor(webId));
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
