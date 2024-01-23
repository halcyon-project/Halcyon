package com.ebremer.halcyon.fuseki.shiro;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.apache.shiro.web.util.WebUtils;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;

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
            
            JEEContext context = new JEEContext((HttpServletRequest) request, (HttpServletResponse) response);
            ProfileManager profileManager = new ProfileManager(context, new JEESessionStore());
            Optional<UserProfile> profile = profileManager.getProfile();
            boolean hahahha = profile.isPresent();
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
