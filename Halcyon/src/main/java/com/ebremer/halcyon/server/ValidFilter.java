package com.ebremer.halcyon.server;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.pac4j.oidc.profile.OidcProfile;

public class ValidFilter implements Filter {

    private Config config;

    @Override
    public void init(FilterConfig filterConfig) {
        // Retrieve or set the Config object
        this.config = (Config) filterConfig.getServletContext().getAttribute("pac4jConfig");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        //WebContext context = new JEEContext((HttpServletRequest) request, (HttpServletResponse) response);
        JEEContext context = new JEEContext((HttpServletRequest) request, (HttpServletResponse) response);
        SessionStore sessionStore = new JEESessionStore();       
        ProfileManager profileManager = new ProfileManager(context, sessionStore);
        Optional<UserProfile> profileOptional = profileManager.getProfile();

        if (profileOptional.isPresent()) {
            OidcProfile oidcProfile = (OidcProfile) profileOptional.get();

            if (oidcProfile.isExpired()) {
                // Token is expired; attempt to refresh
                // (Include token refresh logic here)
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // Cleanup resources if needed
    }
}
