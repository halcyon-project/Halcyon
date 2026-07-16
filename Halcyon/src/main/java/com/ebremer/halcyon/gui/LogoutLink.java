package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import org.apache.wicket.markup.html.link.StatelessLink;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.handler.RedirectRequestHandler;
//import com.ebremer.halcyon.datum.HalcyonPrincipal;

/**
 * Logs out the user by invalidating their session and redirecting them to a
 * Keycloak logout URL.
 */
public class LogoutLink extends StatelessLink<Void> {
    
    private final String keycloakLogoutUrl;

    public LogoutLink(String id) {
        super(id);
        this.keycloakLogoutUrl = buildKeycloakLogoutUrl();
    }
    
    private String buildKeycloakLogoutUrl() {
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

        // Redirect to Keycloak logout endpoint
        RequestCycle.get().scheduleRequestHandlerAfterCurrent(new RedirectRequestHandler(keycloakLogoutUrl));
    }
}
