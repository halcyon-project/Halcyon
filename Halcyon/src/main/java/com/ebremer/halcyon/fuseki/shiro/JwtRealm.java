package com.ebremer.halcyon.fuseki.shiro;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;

public class JwtRealm extends AuthorizingRealm {
    
    public JwtRealm() {}

    @Override
    public boolean supports(AuthenticationToken token) {
        System.out.println("supports");
        return token instanceof JwtToken;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        System.out.println("Implement your authorization logic here based on the user's roles and permissions.");
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
        if (jwtToken.getClaims() == null) {
            // Unreachable while JwtToken's constructor rejects unverified tokens,
            // but this realm must never mint an AuthenticationInfo on trust.
            throw new AuthenticationException("JWT carries no verified claims");
        }
        SimplePrincipalCollection principalCollection = new SimplePrincipalCollection(jwtToken, getName());
        return new SimpleAuthenticationInfo(principalCollection, jwtToken.getCredentials());
    }
}
