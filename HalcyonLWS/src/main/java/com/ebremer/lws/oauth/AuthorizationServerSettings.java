package com.ebremer.lws.oauth;

import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import java.util.List;

/**
 * Where this deployment's embedded LWS authorization server lives, and which storages it issues
 * tokens for.
 *
 * <p>One authorization server per Halcyon instance, not per storage. lws10-core allows either — the
 * authorization server "may be the same server as the storage server or it may be a separate
 * entity" — and one per instance is what the discovery rules make natural: RFC 8414 fixes the
 * metadata at {@code {issuer}/.well-known/lws-configuration}, so an issuer with a path would need
 * the metadata at the host root anyway. The issuer is therefore the instance's own origin, and the
 * {@code aud} of a token is the storage it was minted for, which is what keeps a token for one
 * storage from working on another in the same instance.
 *
 * <p>The paths are fixed rather than configurable. They are discovered — a client reads
 * {@code token_endpoint} and {@code jwks_uri} out of the metadata document and never constructs
 * them — so a knob here would only create a way to misconfigure something nothing hardcodes.
 */
public final class AuthorizationServerSettings {

    /** RFC 8414 + lws10-core: the metadata MUST be at this path. */
    public static final String METADATA_PATH = "/.well-known/lws-configuration";

    /** The URL tree the authorization server's own endpoints live under. */
    public static final String BASE_PATH = "/lws-as";

    public static final String TOKEN_PATH = BASE_PATH + "/token";
    public static final String JWKS_PATH = BASE_PATH + "/jwks";

    private final String origin;
    private final boolean enabled;
    private final long lifetimeSeconds;
    private final boolean acceptCredentialsDirectly;
    private final List<String> realms;

    public AuthorizationServerSettings(String origin, boolean enabled, long lifetimeSeconds,
            boolean acceptCredentialsDirectly, List<String> realms) {
        this.origin = strip(origin);
        this.enabled = enabled;
        this.lifetimeSeconds = lifetimeSeconds;
        this.acceptCredentialsDirectly = acceptCredentialsDirectly;
        this.realms = List.copyOf(realms);
    }

    /** This deployment's settings: the configured origin, flags, and every mounted storage. */
    public static AuthorizationServerSettings fromSettings() {
        LwsSettings lws = LwsSettings.get();
        List<String> realms = lws.storages().stream().map(LwsStorageConfig::realm).toList();
        return new AuthorizationServerSettings(
                com.ebremer.halcyon.server.utils.HalcyonSettings.getSettings().getProxyHostName(),
                lws.authorizationServerEnabled() && !realms.isEmpty(),
                lws.accessTokenLifetimeSeconds(),
                lws.acceptAuthenticationCredentials(),
                realms);
    }

    /**
     * Whether this instance runs an authorization server at all.
     *
     * <p>False with no storages mounted: there would be nothing to issue tokens for, and an
     * endpoint that answers every request {@code invalid_target} is worse than no endpoint.
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * The {@code iss} of every token this server issues, the {@code as_uri} of every challenge, and
     * the base RFC 8414 resolves {@link #METADATA_PATH} against.
     */
    public String issuer() {
        return origin;
    }

    public String metadataUri() {
        return origin + METADATA_PATH;
    }

    public String tokenEndpointUri() {
        return origin + TOKEN_PATH;
    }

    public String jwksUri() {
        return origin + JWKS_PATH;
    }

    /** How long an issued token lives. lws10-core RECOMMENDS 300 seconds or less. */
    public long accessTokenLifetimeSeconds() {
        return lifetimeSeconds;
    }

    /**
     * Whether a storage still accepts a bare authentication credential presented as a bearer token,
     * in addition to an access token from this server.
     *
     * <p>lws10-core allows it — "a server MAY support additional authorization mechanisms beyond
     * this baseline" — and it is on by default because turning it off logs out every existing
     * client of this deployment at once. Turning it off is what makes the audience-confinement
     * property of an exchanged token actually hold: while a credential is accepted directly, a
     * client can skip the exchange and so can anyone who captures the credential.
     */
    public boolean acceptCredentialsDirectly() {
        return acceptCredentialsDirectly;
    }

    /** The storages this server issues tokens for, by the {@code realm} of their challenges. */
    public List<String> realms() {
        return realms;
    }

    /**
     * The {@code aud} to mint for a {@code resource} parameter, or {@code null} if it names no
     * storage of this instance.
     *
     * <p>A storage is identified by its base URI, and its own storage URI carries a trailing slash,
     * so both spellings are accepted and both resolve to the one canonical value. Anything else —
     * including a URI that merely starts with a storage's base, which is how a path-traversal-ish
     * {@code resource} would look — is not a storage and gets no token.
     */
    public String realmFor(String resource) {
        if (resource == null) {
            return null;
        }
        String r = strip(resource);
        return realms.contains(r) ? r : null;
    }

    private static String strip(String uri) {
        if (uri == null) {
            return null;
        }
        String s = uri.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
