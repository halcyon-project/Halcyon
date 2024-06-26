package com.ebremer.halcyon.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.pac4j.core.adapter.FrameworkAdapter;
import org.pac4j.core.config.Config;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.core.util.security.SecurityEndpoint;
import org.pac4j.core.util.security.SecurityEndpointBuilder;
import org.pac4j.jee.config.AbstractConfigFilter;
import org.pac4j.jee.context.JEEFrameworkParameters;
import java.io.IOException;
import org.pac4j.core.resource.SpringResourceHelper;

/**
 * <p>This filter protects an URL.</p>
 *
 * @author Jerome Leleu, Michael Remond
 * @since 1.0.0
 */
@Getter
@Setter
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

    /**
     *
     * @param request
     * @param response
     * @param filterChain
     * @throws IOException
     * @throws ServletException
     */
    @Override
    protected final void internalFilter( final HttpServletRequest request, final HttpServletResponse response, final FilterChain filterChain ) throws IOException, ServletException {
        val config = getSharedConfig();
        FrameworkAdapter.INSTANCE.applyDefaultSettingsIfUndefined(config);
        config.getSecurityLogic().perform(config, (ctx, session, profiles) -> {
            // if no profiles are loaded, pac4j is not concerned with this request
            filterChain.doFilter(profiles.isEmpty() ? request : new HalcyonPac4JHttpServletRequestWrapper(request, profiles), response);
            return null;
        }, clients, authorizers, matchers, new JEEFrameworkParameters(request, response));
    }
}
