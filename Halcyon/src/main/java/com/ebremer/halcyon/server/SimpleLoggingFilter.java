package com.ebremer.halcyon.server;

import jakarta.servlet.Filter;

public class SimpleLoggingFilter { /*
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.oidc.profile.keycloak.KeycloakOidcProfile;
import org.springframework.beans.factory.annotation.Autowired;

public class SimpleLoggingFilter implements Filter {
    
    @Autowired
    private ProfileManager manager;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization code, if needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        System.out.println("Request URI is: " + req.getRequestURI());
        
        if (manager!=null) {
                Optional<KeycloakOidcProfile> profile = manager.getProfile(KeycloakOidcProfile.class);
        if (profile.isPresent()) {
            System.out.println("We have keycloak profile!!!");
            System.out.println(profile.get().getIdToken().serialize());
            System.out.println(profile.get().getFamilyName());
        }}
        
        
        chain.doFilter(request, response); // Continue with the next filter in the chain
    }

    @Override
    public void destroy() {
        // Cleanup code, if needed
    } */
}

