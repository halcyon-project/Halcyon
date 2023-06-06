package com.ebremer.halcyon.fuseki.shiro;

import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;

public class JwtAuthenticatingFilter extends AuthenticatingFilter {

    @Override
    protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) throws Exception {
        //System.out.println("createToken(ServletRequest request, ServletResponse response)");
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String jwt = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (jwt!=null) {
            if (jwt.substring(0, "Bearer ".length()).equals("Bearer ")) {
                jwt = jwt.substring("Bearer ".length(), jwt.length());
                return new JwtToken(jwt);
            }
        }
        return null;
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        //System.out.println("onAccessDenied(ServletRequest request, ServletResponse response)");
        return executeLogin(request, response);
    }
}
