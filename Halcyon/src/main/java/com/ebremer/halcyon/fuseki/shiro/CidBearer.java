package com.ebremer.halcyon.fuseki.shiro;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.auth.InvalidBearerTokenException;
import com.ebremer.lws.auth.PresentedToken;
import com.ebremer.lws.auth.oidc.LwsOidcSettings;
import com.ebremer.lws.auth.oidc.LwsOidcVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies a CID URL (LWS 1.0 OpenID Connect) bearer, using the very same {@link LwsOidcVerifier}
 * the LWS SPARQL endpoint ({@code /rdf2}) uses: an ID Token whose {@code sub} is a CID URL, trusted
 * by dereferencing that CID URL's controlled identifier document to the issuing OpenID Provider.
 * This is the CID URL login path — {@link JwtToken} accepts such a credential (the CID URL becomes the
 * authenticated identity), instead of the Keycloak-only path 401'ing it.
 *
 * <p>Off unless {@code lws-oidc.json} enables the LWS-OIDC verifier; otherwise only Keycloak
 * credentials are accepted. The verifier is shared (JWKS cached across requests).
 */
final class CidBearer {

    private static final Logger LOG = LoggerFactory.getLogger(CidBearer.class);

    private static volatile boolean loaded;
    private static volatile boolean enabled;
    private static volatile LwsOidcVerifier verifier;

    private CidBearer() {
    }

    /**
     * The verified CID URL for an LWS-OIDC bearer, or {@code null} when LWS-OIDC is disabled, the token
     * is not an LWS credential (its {@code sub} is not a CID URL), or it fails verification.
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
            return ctx == null ? null : ctx.cidUrl();
        } catch (InvalidBearerTokenException e) {
            LOG.debug("LWS-OIDC bearer rejected at the Fuseki store: {}", e.getMessage());
            return null;
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (CidBearer.class) {
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
