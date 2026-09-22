package com.ebremer.lws.oauth;

import com.ebremer.lws.auth.AccessTokenValidator;
import com.ebremer.lws.auth.CredentialVerifier;
import com.ebremer.lws.auth.oidc.LwsOidcSettings;
import com.ebremer.lws.auth.oidc.LwsOidcVerifier;
import com.ebremer.lws.store.LwsStore;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This deployment's embedded LWS authorization server, assembled once at startup.
 *
 * <p>lws10-core's authorization framework has three moving parts and they have to agree: the
 * metadata document a client discovers, the token endpoint that issues, and the validator each
 * storage checks with. One object owns all three so they cannot disagree — the signing key the
 * endpoint uses is the verification key the validator holds and the key the {@code jwks_uri}
 * publishes, and the issuer in a token is the {@code as_uri} of every challenge.
 *
 * <p>A singleton primed by {@link #init}, for the same reason the webhook key and the cursor secret
 * are: the signing key is persisted, and reading it needs a write transaction the first time, which
 * cannot be opened inside the read transaction a request holds. So it is built at startup, outside
 * any request, or not at all — {@link #get} returns {@code null} when this instance runs no
 * authorization server, and the credential chain then falls back to accepting credentials directly.
 *
 * <p>The subject-token suites are the <em>same verifier objects' kind</em> as the storages use: an
 * identity that can authenticate to a storage directly is exactly one that can be exchanged for a
 * token, so there is no second definition of a valid credential to drift out of step.
 */
public final class LwsAuthorizationServer {

    private static final Logger LOG = LoggerFactory.getLogger(LwsAuthorizationServer.class);

    private static volatile LwsAuthorizationServer instance;

    private final AuthorizationServerSettings settings;
    private final AccessTokenKeys keys;
    private final TokenExchange exchange;

    private LwsAuthorizationServer(AuthorizationServerSettings settings, AccessTokenKeys keys,
            TokenExchange exchange) {
        this.settings = settings;
        this.keys = keys;
        this.exchange = exchange;
    }

    /**
     * Build the authorization server for this deployment, or record that there is none.
     *
     * <p>Idempotent, and safe to call before any storage is mounted — it reads the mounted storages
     * from the settings, so it must be called after those are parsed and before the first request.
     *
     * @return the server, or {@code null} when this instance runs none
     */
    public static synchronized LwsAuthorizationServer init(LwsStore store) {
        if (instance != null) {
            return instance;
        }
        AuthorizationServerSettings settings = AuthorizationServerSettings.fromSettings();
        if (!settings.enabled()) {
            LOG.info("LWS authorization server disabled; storages accept authentication "
                    + "credentials directly");
            return null;
        }
        AccessTokenKeys keys = new AccessTokenKeys(store);
        TokenExchange exchange = new TokenExchange(settings, keys, subjectTokenSuites(),
                Clock.systemUTC());
        instance = new LwsAuthorizationServer(settings, keys, exchange);
        LOG.info("LWS authorization server at {} (token {}, jwks {}, kid {}, {}s tokens) for {}",
                settings.issuer(), AuthorizationServerSettings.TOKEN_PATH,
                AuthorizationServerSettings.JWKS_PATH, keys.keyId(),
                settings.accessTokenLifetimeSeconds(), settings.realms());
        if (exchange.subjectTokenTypes().isEmpty()) {
            LOG.warn("LWS authorization server has no authentication suite configured: it can "
                    + "issue nothing until an OpenID provider is (settings.ttl :AuthServer, or "
                    + "lws-oidc.json)");
        }
        if (!settings.acceptCredentialsDirectly()) {
            LOG.info("storages accept ONLY access tokens from {}", settings.issuer());
        }
        return instance;
    }

    /** The server, or {@code null} when this instance runs none (or {@link #init} has not run). */
    public static LwsAuthorizationServer get() {
        return instance;
    }

    /** For tests: forget the primed instance. */
    static synchronized void reset() {
        instance = null;
    }

    /**
     * The authentication suites a subject token may be validated by.
     *
     * <p>Only the OpenID suite. The Keycloak bearer verifier is deliberately not here: it validates
     * an <em>access</em> token minted by Keycloak for this resource server, which is not an
     * authentication credential about an agent — exchanging one would be laundering a token issued
     * for one audience into a token for another. A Keycloak-authenticated client exchanges its ID
     * Token through the OpenID suite, or presents its Keycloak token directly while
     * {@code :LWSAcceptAuthenticationCredentials} is on.
     */
    private static List<CredentialVerifier> subjectTokenSuites() {
        List<CredentialVerifier> suites = new ArrayList<>();
        LwsOidcSettings lws = LwsOidcSettings.load();
        if (lws.enabled()) {
            suites.add(new LwsOidcVerifier(lws));
        }
        return suites;
    }

    public AuthorizationServerSettings settings() {
        return settings;
    }

    public AccessTokenKeys keys() {
        return keys;
    }

    public TokenExchange exchange() {
        return exchange;
    }

    /** The validator a storage with this {@code realm} checks access tokens with. */
    public AccessTokenValidator validatorFor(String realm) {
        return new AccessTokenValidator(settings, keys, realm);
    }
}
