package com.ebremer.halcyon.fuseki.shiro;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import org.apache.shiro.web.util.WebUtils;

public class JwtAuthenticatingFilter extends AuthenticatingFilter {

    @Override
    protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) throws Exception {
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
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) {
        boolean loggedIn = false;
        try {
            loggedIn = executeLogin(request, response);
        } catch (IllegalStateException ex) {
            System.out.println(ex.getMessage());
        } catch (Exception ex) {
            Logger.getLogger(JwtAuthenticatingFilter.class.getName()).log(Level.SEVERE, null, ex);
        }
        return loggedIn || sendChallenge(response);
    }
    
    private boolean sendChallenge(ServletResponse response) {
        HttpServletResponse httpResponse = WebUtils.toHttp(response);
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }
}
