package com.ebremer.halcyon.fuseki.shiro;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.apache.shiro.web.util.WebUtils;

public class JwtAuthenticatingFilter extends AuthenticatingFilter {

    private static final Logger logger = Logger.getLogger(JwtAuthenticatingFilter.class.getName());

    @Override
    protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) throws Exception {
        logger.log(Level.FINE, "createToken");
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String jwt = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        try {
            if (jwt != null) {
                if (jwt.substring(0, "Bearer ".length()).equals("Bearer ")) {
                    jwt = jwt.substring("Bearer ".length(), jwt.length());
                    return new JwtToken(jwt);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error extracting JWT: {0}", e.getMessage());
        }

        return null; // No valid token found
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) {
        logger.log(Level.FINE, "onAccessDenied");
        boolean loggedIn = false;
        try {
            loggedIn = executeLogin(request, response);
        } catch (IllegalStateException ex) {
            logger.log(Level.SEVERE, "Invalid JWT: {0}", ex.getMessage());
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected error: {0}", ex.getMessage());
        }
        return loggedIn || sendChallenge(response);
    }

    private boolean sendChallenge(ServletResponse response) {
        logger.log(Level.FINE, "sendChallenge");
        HttpServletResponse httpResponse = WebUtils.toHttp(response);
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // Set status to 401
        return false; // Challenge was sent, return false
    }
}
