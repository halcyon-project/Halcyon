package com.ebremer.halcyon.fuseki.shiro;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtRealm extends AuthorizingRealm {
    private static final Logger logger = LoggerFactory.getLogger(JwtRealm.class);
    
    public JwtRealm() {}

    @Override
    public boolean supports(AuthenticationToken token) {
        logger.debug("supports");
        return token instanceof JwtToken;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        logger.debug("Implement your authorization logic here based on the user's roles and permissions.");
        return null;
    }

    /**
     * H2: assert the token actually carries VERIFIED claims.
     * <p>
     * This realm's credentials matcher is Shiro's {@code SimpleCredentialsMatcher},
     * and this method used to return the JWT string itself as the credentials —
     * so the matcher compared the token to the token and always "matched". It
     * therefore verified nothing; the only thing standing between a forged token
     * and an authenticated subject was an incidental NPE inside
     * {@code HalcyonPrincipal}. Trust is now asserted here, explicitly, on the
     * verified claims produced by {@link JwtVerifier} (signature + kid + issuer +
     * azp + required claims).
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
        if (!(token instanceof JwtToken jwtToken)) {
            throw new AuthenticationException("Unsupported authentication token");
        }
        if (jwtToken.getPrincipal() == null) {
            // Unreachable while JwtToken's constructor rejects unverified tokens (Keycloak or
            // LWS-OIDC/WebID), but this realm must never mint an AuthenticationInfo on trust. The
            // principal — not the Keycloak claims, which are null for a WebID credential — is the
            // proof of a verified identity here.
            throw new AuthenticationException("JWT carries no verified identity");
        }
        SimplePrincipalCollection principalCollection = new SimplePrincipalCollection(jwtToken, getName());
        return new SimpleAuthenticationInfo(principalCollection, jwtToken.getCredentials());
    }
}
