package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.auth.oidc.LwsOidcSettings;
import com.ebremer.lws.auth.oidc.WebIdOidcLogin;
import java.util.Map;
import java.util.Set;

/**
 * Shared configuration for the interactive WebID login servlets (Option B): the memoized
 * {@link WebIdOidcLogin} flow (built from {@code lws-oidc.json} plus the app's proxy host) and the
 * HTTP-session attribute names the two servlets hand state through. Off unless {@code lws-oidc.json}
 * sets {@code "enabled": true}.
 */
public final class WebIdLogin {

    /** Session attribute holding the in-flight {@link WebIdOidcLogin.Pending} between the two servlets. */
    static final String PENDING = "halcyon.webidlogin.pending";
    /** Session attribute holding the authenticated WebID, for {@code HalcyonSession} to seat (B3). */
    public static final String WEBID = "halcyon.webidlogin.webid";
    /** Session attribute holding the login's {@link WebIdOidcLogin.Tokens} — the ID Token (a bare
     *  LWS-OIDC credential the GUI presents to LWS storage as this WebID) plus the refresh token and
     *  the OP coordinates to refresh it. A WebID login has no Keycloak access token. */
    public static final String TOKENS = "halcyon.webidlogin.tokens";
    /** Callback path — also the OAuth {@code redirect_uri} registered at the OP. */
    static final String CALLBACK_PATH = "/webid-callback";

    private static volatile boolean loaded;
    private static volatile boolean enabled;
    private static volatile WebIdOidcLogin flow;
    private static volatile Map<String, Set<String>> webIdGroups = Map.of();
    private static volatile Set<String> allowedHosts = Set.of();

    private WebIdLogin() {
    }

    public static boolean enabled() {
        ensureLoaded();
        return enabled;
    }

    static WebIdOidcLogin flow() {
        ensureLoaded();
        return flow;
    }

    /**
     * The locally-configured groups for a signed-in WebID (Option B role mapping), or an empty set.
     * Local policy only — group/role membership is never taken from the OP's token, so no OpenID
     * Provider a WebID happens to name can grant a local role such as {@code admin}.
     */
    public static Set<String> groupsFor(String webId) {
        ensureLoaded();
        return webIdGroups.getOrDefault(webId, Set.of());
    }

    /** The SSRF allow-list ({@code allowedInternalHosts}) — the WebID principal needs it to refresh
     *  its ID Token (reach the OP's token endpoint) outside a {@link WebIdOidcLogin} instance. */
    public static Set<String> allowedHosts() {
        ensureLoaded();
        return allowedHosts;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (WebIdLogin.class) {
            if (loaded) {
                return;
            }
            LwsOidcSettings settings = LwsOidcSettings.load();
            enabled = settings.enabled();
            webIdGroups = settings.webIdGroups();
            allowedHosts = settings.allowedInternalHosts();
            if (enabled) {
                String redirectUri = HalcyonSettings.getSettings().getProxyHostName() + CALLBACK_PATH;
                // Which providers this deployment accepts, from settings.ttl. Unset means allow
                // all, so an unconfigured install logs in exactly as it did before.
                flow = new WebIdOidcLogin(settings.webIdLoginClientId(), redirectUri,
                        settings.webIdLoginDynamicRegistration(), settings.allowedInternalHosts())
                        .withTrust(() -> com.ebremer.lws.config.LwsSettings.get().issuerPolicy(),
                                () -> com.ebremer.lws.config.LwsSettings.get().webIdHostPolicy());
            }
            loaded = true;
        }
    }
}
