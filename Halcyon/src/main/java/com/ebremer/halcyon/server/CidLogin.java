package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.auth.oidc.LwsOidcSettings;
import com.ebremer.lws.auth.oidc.CidOidcLogin;
import java.util.Map;
import java.util.Set;

/**
 * Shared configuration for the interactive CID URL login servlets (Option B): the memoized
 * {@link CidOidcLogin} flow (built from {@code lws-oidc.json} plus the app's proxy host) and the
 * HTTP-session attribute names the two servlets hand state through. Off unless {@code lws-oidc.json}
 * sets {@code "enabled": true}.
 */
public final class CidLogin {

    /** Session attribute holding the in-flight {@link CidOidcLogin.Pending} between the two servlets. */
    static final String PENDING = "halcyon.cidlogin.pending";
    /** Session attribute holding the authenticated CID URL, for {@code HalcyonSession} to seat (B3). */
    public static final String CID_URL = "halcyon.cidlogin.cidurl";
    /** Session attribute holding the login's {@link CidOidcLogin.Tokens} — the ID Token (a bare
     *  LWS-OIDC credential the GUI presents to LWS storage as this CID URL) plus the refresh token and
     *  the OP coordinates to refresh it. A CID URL login has no Keycloak access token. */
    public static final String TOKENS = "halcyon.cidlogin.tokens";

    private static volatile boolean loaded;
    private static volatile boolean enabled;
    private static volatile CidOidcLogin flow;
    private static volatile Map<String, Set<String>> cidGroups = Map.of();
    private static volatile Set<String> allowedHosts = Set.of();

    private CidLogin() {
    }

    public static boolean enabled() {
        ensureLoaded();
        return enabled;
    }

    static CidOidcLogin flow() {
        ensureLoaded();
        return flow;
    }

    /**
     * The locally-configured groups for a signed-in CID URL (Option B role mapping), or an empty set.
     * Local policy only — group/role membership is never taken from the OP's token, so no OpenID
     * Provider a CID URL happens to name can grant a local role such as {@code admin}.
     */
    public static Set<String> groupsFor(String cidUrl) {
        ensureLoaded();
        return cidGroups.getOrDefault(cidUrl, Set.of());
    }

    /** The SSRF allow-list ({@code allowedInternalHosts}) — the CID URL principal needs it to refresh
     *  its ID Token (reach the OP's token endpoint) outside a {@link CidOidcLogin} instance. */
    public static Set<String> allowedHosts() {
        ensureLoaded();
        return allowedHosts;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (CidLogin.class) {
            if (loaded) {
                return;
            }
            LwsOidcSettings settings = LwsOidcSettings.load();
            enabled = settings.enabled();
            cidGroups = settings.cidGroups();
            allowedHosts = settings.allowedInternalHosts();
            if (enabled) {
                // The OAuth redirect_uri: /cid-callback, or /webid-callback for a pre-arranged client
                // still configured the old way (LwsOidcSettings.from). Both paths are served.
                String redirectUri = HalcyonSettings.getSettings().getProxyHostName() + settings.callbackPath();
                // Which providers this deployment accepts, from settings.ttl. Unset means allow
                // all, so an unconfigured install logs in exactly as it did before.
                flow = new CidOidcLogin(settings.cidLoginClientId(), redirectUri,
                        settings.cidLoginDynamicRegistration(), settings.allowedInternalHosts())
                        .withTrust(() -> com.ebremer.lws.config.LwsSettings.get().issuerPolicy(),
                                () -> com.ebremer.lws.config.LwsSettings.get().cidHostPolicy());
            }
            loaded = true;
        }
    }
}
