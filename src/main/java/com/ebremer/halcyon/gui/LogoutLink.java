package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.HalcyonSettings;
import org.apache.wicket.markup.html.link.StatelessLink;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.handler.RedirectRequestHandler;


public class LogoutLink extends StatelessLink<Void> {

    private final String keycloakLogoutUrl = HalcyonSettings.getSettings().getHostName()+"/auth/realms/"+HalcyonSettings.getSettings().getRealm()+"/protocol/openid-connect/logout?client_id=account";

    public LogoutLink(String id) {
        super(id);
    }

    @Override
    public void onClick() {
        //System.out.println(keycloakLogoutUrl);
        //HalcyonSession.get().getHalcyonPrincipal()
        //String token = HalcyonSession.get().getToken();
        String x = keycloakLogoutUrl;
        //x=x+"&id_token_hint="+token;
        //x = x + "&post_logout_redirect_uri=http://localhost:8888";
        System.out.println("LOGOUT : "+x);
        //String ha = OIDCLoginProtocol.CLIENT_ID_PARAM;
        getSession().invalidate();
        RequestCycle.get().scheduleRequestHandlerAfterCurrent(new RedirectRequestHandler(x));
    }
}
