package com.ebremer.halcyon.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.pac4j.core.adapter.FrameworkAdapter;
import org.pac4j.core.config.Config;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.core.util.security.SecurityEndpoint;
import org.pac4j.core.util.security.SecurityEndpointBuilder;
import org.pac4j.jee.config.AbstractConfigFilter;
import org.pac4j.jee.context.JEEFrameworkParameters;
import java.io.IOException;

/**
 * <p>This filter protects an URL.</p>
 *
 * @author Erich Bremer
 */
public class HalcyonSecurityFilter extends AbstractConfigFilter implements SecurityEndpoint {
    private String clients;
    private String authorizers;
    private String matchers;

    public HalcyonSecurityFilter() {}

    public HalcyonSecurityFilter(final Config config) {
        setConfig(config);
    }

    public HalcyonSecurityFilter(final Config config, final String clients) {
        this(config);
        this.clients = clients;
    }

    public HalcyonSecurityFilter(final Config config, final String clients, final String authorizers) {
        this(config, clients);
        this.authorizers = authorizers;
    }

    public HalcyonSecurityFilter(final Config config, final String clients, final String authorizers, final String matchers) {
        this(config, clients, authorizers);
        this.matchers = matchers;
    }

    public static HalcyonSecurityFilter build(final Object... parameters) {
        final HalcyonSecurityFilter securityFilter = new HalcyonSecurityFilter();
        SecurityEndpointBuilder.buildConfig(securityFilter, parameters);
        return securityFilter;
    }

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        super.init(filterConfig);
        this.clients = getStringParam(filterConfig, Pac4jConstants.CLIENTS, this.clients);
        this.authorizers = getStringParam(filterConfig, Pac4jConstants.AUTHORIZERS, this.authorizers);
        this.matchers = getStringParam(filterConfig, Pac4jConstants.MATCHERS, this.matchers);
    }

    @Override
    protected final void internalFilter( final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain ) throws IOException, ServletException {
        // Option B: a request already authenticated by the interactive WebID login (its callback set
        // this attribute only after complete() validated the OP's ID token and bound it to the typed
        // WebID) carries its identity as a session attribute, not a pac4j/Keycloak profile. Without
        // this, pac4j finds no profile on a secured URL and redirects the signed-in user to Keycloak.
        // Let it through: the Wicket HalcyonAuthorizationStrategy still enforces per-page access
        // (authenticated vs admin) from the seated WebID principal, and ACP enforces per resource.
        HttpSession webidSession = request.getSession(false);
        if (webidSession != null && webidSession.getAttribute(WebIdLogin.WEBID) != null) {
            filterChain.doFilter(request, response);
            return;
        }
        var config = getSharedConfig();
        FrameworkAdapter.INSTANCE.applyDefaultSettingsIfUndefined(config);
        config.getSecurityLogic().perform(config, (ctx, session, profiles) -> {
            // if no profiles are loaded, pac4j is not concerned with this request
            filterChain.doFilter(profiles.isEmpty() ? request : new HalcyonPac4JHttpServletRequestWrapper(request, profiles), response);
            return null;
        }, clients, authorizers, matchers, new JEEFrameworkParameters(request, response));
    }

    @Override
    public void setClients(String clients) {
        this.clients = clients;
    }

    @Override
    public void setAuthorizers(String authorizers) {
        this.authorizers = authorizers;
    }

    @Override
    public void setMatchers(String matchers) {
        this.matchers = matchers;
    }
}
