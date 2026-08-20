package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Registers a bean only when the Keycloak subsystem is switched on, which it is iff
 * {@code settings.ttl} names an {@code :AuthServer}.
 *
 * <p>The subsystem is disabled by <em>commenting the line out</em>, and nothing about it
 * is removed: the pac4j client, the {@code /callback} and logout filters, the security
 * filters over the Wicket pages and the {@code /auth} reverse proxy all still exist and
 * come back exactly as they were the moment the setting returns. What changes is only
 * whether Spring instantiates them.
 *
 * <p>Skipping construction is the point, not merely tidiness. {@code Cool.config()} builds
 * a {@code KeycloakOidcClient}, and the LWS bearer verifier performs OIDC discovery in its
 * constructor — both reach out to the authorization server as they are made. Left to run
 * against a Keycloak that is not there, they turn startup into a wait for a connection
 * that will never be accepted.
 *
 * <p>The condition reads {@link HalcyonSettings}, not the Spring {@code Environment}: the
 * settings singleton is loaded from {@code settings.ttl} by {@code INIT.init()} in
 * {@code main()}, before the context is built, so it is already there to be asked.
 */
public final class KeycloakEnabled implements Condition {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakEnabled.class);

    private static volatile boolean announced;

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean on = HalcyonSettings.getSettings().isKeycloakEnabled();
        announceOnce(on);
        return on;
    }

    /**
     * Say once, at startup, which authentication stack is live. Which one is running is
     * not something an operator should have to infer from the absence of a stack trace.
     */
    private static void announceOnce(boolean on) {
        if (announced) {
            return;
        }
        announced = true;
        if (on) {
            logger.info("Keycloak authentication ENABLED (:AuthServer {})",
                    HalcyonSettings.getSettings().getAuthServer());
        } else {
            logger.warn("Keycloak authentication DISABLED — no :AuthServer in settings.ttl. "
                    + "Sign-in runs on the LWS auth system (WebID-OIDC, lws-oidc.json); the "
                    + "Keycloak client, its /callback and logout filters and the /auth proxy "
                    + "are not registered. Restore the :AuthServer line to bring them back.");
        }
    }
}
