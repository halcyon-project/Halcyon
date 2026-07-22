package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.auth.oidc.LwsOidcSettings;
import com.ebremer.lws.auth.oidc.WebIdOidcLogin;

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
    /** Callback path — also the OAuth {@code redirect_uri} registered at the OP. */
    static final String CALLBACK_PATH = "/webid-callback";

    private static volatile boolean loaded;
    private static volatile boolean enabled;
    private static volatile WebIdOidcLogin flow;

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
            if (enabled) {
                String redirectUri = HalcyonSettings.getSettings().getProxyHostName() + CALLBACK_PATH;
                flow = new WebIdOidcLogin(settings.webIdLoginClientId(), redirectUri,
                        settings.webIdLoginDynamicRegistration(), settings.allowedInternalHosts());
            }
            loaded = true;
        }
    }
}
