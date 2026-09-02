package com.ebremer.halcyon.fuseki.shiro;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.auth.InvalidBearerTokenException;
import com.ebremer.lws.auth.PresentedToken;
import com.ebremer.lws.auth.oidc.LwsOidcSettings;
import com.ebremer.lws.auth.oidc.LwsOidcVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies a WebID (LWS 1.0 OpenID Connect) bearer, using the very same {@link LwsOidcVerifier}
 * the LWS SPARQL endpoint ({@code /rdf2}) uses: an ID Token whose {@code sub} is a WebID, trusted
 * by dereferencing that WebID's controlled identifier document to the issuing OpenID Provider.
 * This is the WebID-login path — {@link JwtToken} accepts such a credential (the WebID becomes the
 * authenticated identity), instead of the Keycloak-only path 401'ing it.
 *
 * <p>Off unless {@code lws-oidc.json} enables the LWS-OIDC verifier; otherwise only Keycloak
 * credentials are accepted. The verifier is shared (JWKS cached across requests).
 */
final class WebIdBearer {

    private static final Logger LOG = LoggerFactory.getLogger(WebIdBearer.class);

    private static volatile boolean loaded;
    private static volatile boolean enabled;
    private static volatile LwsOidcVerifier verifier;

    private WebIdBearer() {
    }

    /**
     * The verified WebID for an LWS-OIDC bearer, or {@code null} when LWS-OIDC is disabled, the token
     * is not an LWS credential (its {@code sub} is not a WebID), or it fails verification.
     */
    static String verify(String token) {
        ensureLoaded();
        if (!enabled) {
            return null;
        }
        PresentedToken presented;
        try {
            presented = PresentedToken.parse("Bearer " + token);
        } catch (InvalidBearerTokenException e) {
            return null;
        }
        try {
            AgentContext ctx = verifier.tryAuthenticate(presented, null);
            return ctx == null ? null : ctx.webId();
        } catch (InvalidBearerTokenException e) {
            LOG.debug("LWS-OIDC bearer rejected at the Fuseki store: {}", e.getMessage());
            return null;
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (WebIdBearer.class) {
            if (loaded) {
                return;
            }
            LwsOidcSettings settings = LwsOidcSettings.load();
            enabled = settings.enabled();
            if (enabled) {
                verifier = new LwsOidcVerifier(settings);
            }
            loaded = true;
        }
    }
}
