package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import org.pac4j.core.authorization.authorizer.IsAnonymousAuthorizer;
import org.pac4j.core.authorization.authorizer.IsAuthenticatedAuthorizer;
import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.config.ConfigFactory;
import org.pac4j.core.matching.matcher.PathMatcher;
import org.pac4j.oidc.client.KeycloakOidcClient;
import org.pac4j.oidc.config.KeycloakOidcConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.pac4j.oidc.exceptions.OidcTokenException;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

/**
 * Configures and builds security settings for the application.
 */
@Configuration
public class HalcyonConfigFactory implements ConfigFactory {

    private static final Logger logger = LoggerFactory.getLogger(HalcyonConfigFactory.class);

    @Autowired
    @Lazy
    KeycloakOidcClient keycloakclient;
    
    @Autowired
    private KeycloakOidcConfiguration keycloakOidcConfiguration;
    
    @Bean
    public KeycloakOidcClient keycloakOidcClient() {
        return new KeycloakOidcClient(keycloakOidcConfiguration);
    }

    @Override
    public Config build(final Object... parameters) {
        try {
            keycloakclient.setProfileCreator(new HalcyonProfileCreator());
            final Clients clients = new Clients(HalcyonSettings.getSettings().getProxyHostName() + "/callback", keycloakclient);
            final Config config = new Config(clients);

            config.addAuthorizer("admin", new RequireAnyRoleAuthorizer("ROLE_ADMIN"));
            config.addAuthorizer("custom", new CustomAuthorizer());
            config.addAuthorizer("mustBeAnon", new IsAnonymousAuthorizer("/?mustBeAnon"));
            config.addAuthorizer("mustBeAuth", new IsAuthenticatedAuthorizer("/?mustBeAuth"));
            config.addMatcher("excludedPath", new PathMatcher().excludeRegex("^/callback$"));

            return config;
        } catch (OidcTokenException e) {
            logger.error("Error while setting up Keycloak OIDC client: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to configure Keycloak OIDC client", e);
        } catch (Exception e) {
            logger.error("Unexpected error while building the config: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build the config", e);
        }
    }
}
