package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import org.apache.wicket.markup.html.link.StatelessLink;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.handler.RedirectRequestHandler;
//import com.ebremer.halcyon.datum.HalcyonPrincipal;

/**
 * Logs out the user by invalidating their session and, when Keycloak is running,
 * continuing to its end-session endpoint so the SSO session goes too.
 *
 * <p>With Keycloak switched off (no {@code :AuthServer} in {@code settings.ttl}) there is
 * no such endpoint — {@code /auth/*} is not even mounted — so the redirect would land the
 * user on a 404 having, confusingly, actually logged them out. A WebID session lives
 * entirely in this server's session, so invalidating it IS the logout; the user goes home.
 */
public class LogoutLink extends StatelessLink<Void> {
    
    private final String logoutUrl;

    public LogoutLink(String id) {
        super(id);
        this.logoutUrl = buildKeycloakLogoutUrl();
    }
    
    private String buildKeycloakLogoutUrl() {
        if (!HalcyonSettings.getSettings().isKeycloakEnabled()) {
            return HalcyonSettings.getSettings().getHostName() + "/";
        }
        String baseUrl = HalcyonSettings.getSettings().getHostName() + "/auth/realms/" + HalcyonSettings.getSettings().getRealm() + "/protocol/openid-connect/logout";
        String clientId = "account";
        //String redirectUri = HalcyonSettings.getSettings().getHostName();
        //HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
        //String idToken = hp.getToken();

        // Construct the logout URL with necessary parameters
        String logoutUrl = baseUrl + "?client_id=" + clientId;
        //logoutUrl += "&redirect_uri=" + redirectUri;
        //logoutUrl += "&post_logout_redirect_uri=" + redirectUri;
        //logoutUrl += "&id_token_hint=" + idToken;

        //String ha = OIDCLoginProtocol.CLIENT_ID_PARAM;
        
        return logoutUrl;
    }

    @Override
    public void onClick() {
        // Invalidate the session
        getSession().invalidate();

        // Continue to Keycloak's end-session endpoint, or just home when it is off.
        RequestCycle.get().scheduleRequestHandlerAfterCurrent(new RedirectRequestHandler(logoutUrl));
    }
}
