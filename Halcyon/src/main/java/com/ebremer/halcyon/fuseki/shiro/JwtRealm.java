package com.ebremer.halcyon.fuseki.shiro;

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
        String jwt = jwtToken.getJwt();
        try {
            SimplePrincipalCollection principalCollection = new SimplePrincipalCollection(jwtToken, getName());
            return new SimpleAuthenticationInfo(principalCollection, jwt);
        } catch (Exception e) {
            throw new Error("Invalid JWT token", e);
        }
    }
}
