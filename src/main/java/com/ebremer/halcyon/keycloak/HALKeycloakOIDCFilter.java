package com.ebremer.halcyon.keycloak;

import java.io.IOException;
import java.util.List;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.FilterHolder;
import org.keycloak.adapters.AuthenticatedActionsHandler;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.PreAuthActionsHandler;
import org.keycloak.adapters.servlet.FilterRequestAuthenticator;
import org.keycloak.adapters.servlet.KeycloakOIDCFilter;
import org.keycloak.adapters.servlet.OIDCFilterSessionStore;
import org.keycloak.adapters.servlet.OIDCServletHttpFacade;
import org.keycloak.adapters.spi.AuthChallenge;
import org.keycloak.adapters.spi.AuthOutcome;
import org.keycloak.adapters.spi.SessionIdMapper;
import org.keycloak.adapters.spi.UserSessionManagement;

/**
 *
 * @author erich
 */
public class HALKeycloakOIDCFilter extends KeycloakOIDCFilter {
    private KeycloakOIDCFilterConfig config = null;

    private boolean shouldSkip(HttpServletRequest request) {
        if (skipPattern == null) {
            return false;
        }
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        return skipPattern.matcher(requestPath).matches();
    }
    
    public void setConfig(KeycloakOIDCFilterConfig config) {
        this.config = config;
    }
    
    public void setSessionIdMapper(SessionIdMapper mapper) {
        idMapper = mapper;
    }
    
    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        filterConfig.getInitParameterNames().asIterator().forEachRemaining(p->{
            System.out.println("PARAM --> "+p);
        });
        
        if (!filterConfig.getInitParameterNames().hasMoreElements()) {
            super.init(config);
        } else {
        System.out.println("INIT -------------------> "+filterConfig.getClass().toGenericString());
        if (filterConfig instanceof FilterHolder fh) {
            if (fh.getInitParameter(KeycloakOIDCFilter.CONFIG_FILE_PARAM)==null) {
                fh.setInitParameter(KeycloakOIDCFilter.CONFIG_FILE_PARAM, "keycloak.json");
            }
        }
        super.init(filterConfig);
        }
    }
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        //System.out.println("doFilter =======> "+request.getRequestURI());
        boolean skipme = shouldSkip(request);
        if (skipme) {
            chain.doFilter(req, res);
            return;
        }

        OIDCServletHttpFacade facade = new OIDCServletHttpFacade(request, response);
        KeycloakDeployment deployment = deploymentContext.resolveDeployment(facade);
        if (deployment == null || !deployment.isConfigured()) {
            response.sendError(403);
            return;
        }

        PreAuthActionsHandler preActions = new PreAuthActionsHandler(new UserSessionManagement() {
            @Override
            public void logoutAll() {
                if (idMapper != null) {
                    idMapper.clear();
                }
            }

            @Override
            public void logoutHttpSessions(List<String> ids) {
                System.err.println("**************** logoutHttpSessions");
                for (String id : ids) {
                    idMapper.removeSession(id);
                }
            }
        }, deploymentContext, facade);
        if (preActions.handleRequest()) {
            System.err.println("**************** preActions.handleRequest happened!");
            return;
        }
        nodesRegistrationManagement.tryRegister(deployment);
        OIDCFilterSessionStore tokenStore = new OIDCFilterSessionStore(request, facade, 100000, deployment, idMapper);
        tokenStore.checkCurrentToken();
        FilterRequestAuthenticator authenticator = new FilterRequestAuthenticator(deployment, tokenStore, facade, request, 8443);
        AuthOutcome outcome = authenticator.authenticate();
        if (outcome == AuthOutcome.AUTHENTICATED) {
            if (facade.isEnded()) {
                return;
            }
            AuthenticatedActionsHandler actions = new AuthenticatedActionsHandler(deployment, facade);
            if (actions.handledRequest()) {
                return;
            } else {
                /*
                KeycloakSecurityContext securityContext = (KeycloakSecurityContext) request.getAttribute(KeycloakSecurityContext.class.getName());
                if (securityContext != null) {
                    
                    String jwtToken = securityContext.getTokenString();
                    System.out.println("ADDING TOKEN!!! "+jwtToken);
                    HttpServletResponse httpResponse = (HttpServletResponse) response;
                    httpResponse.setHeader("token", jwtToken);
                } else {
                    System.out.println("NOTHING TO ADD!!!");
                }
                */
                
                HttpServletRequestWrapper wrapper = tokenStore.buildWrapper();
                chain.doFilter(wrapper, res);
                return;
            }
        }
        AuthChallenge challenge = authenticator.getChallenge();
        if (challenge != null) {
            challenge.challenge(facade);
            return;
        }
        response.sendError(403);
    }
}
