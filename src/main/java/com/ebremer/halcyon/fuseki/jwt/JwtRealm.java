package com.ebremer.halcyon.fuseki.jwt;

import io.jsonwebtoken.Claims;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;

public class JwtRealm extends AuthorizingRealm {
    
    public JwtRealm() {
        
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof JwtToken;
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        System.out.println("Implement your authorization logic here based on the user's roles and permissions.");
        return null;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
        JwtToken jwtToken = (JwtToken) token;
        Claims claims = jwtToken.getClaims();
        SimplePrincipalCollection principalCollection = new SimplePrincipalCollection(claims, getName());
        return new SimpleAuthenticationInfo(principalCollection, token.getCredentials());
    }
}


/*
public class JwtRealm extends AuthenticatingRealm {
    private PublicKey publicKey = null;
    
    public JwtRealm() {
        System.out.println("JWT Realm initialized...");
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof JwtToken;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        System.out.println("doGetAuthenticationInfo(AuthenticationToken token) "+publicKey);
        if (publicKey==null) {
            GetPublicKey gpk = new GetPublicKey();
            publicKey = gpk.getPublicKey();
        }
        JwtToken jwtToken = (JwtToken) token;
        String jwt = jwtToken.getToken();
        try {
            Jws<Claims> jws = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(jwt);
            String subject = jws.getBody().getSubject();
            return new SimpleAuthenticationInfo(subject, jwt, getName());
        } catch (JwtException e) {
            throw new AuthenticationException("Invalid JWT", e);
        }
    }
}
*/