package com.ebremer.halcyon.sparql;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import com.ebremer.halcyon.server.utils.HalcyonSettings;

/**
 *
 * @author tdiprima
 */
@WebServlet("/invalidateSession")
public class InvalidateSessionServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            System.out.println("Invalidating session: " + session.getId());
            session.invalidate();
        }

        HalcyonSettings settings = HalcyonSettings.getSettings();
        String keycloakLogoutUrl = settings.getHostName() + "/auth/realms/" + settings.getRealm() + "/protocol/openid-connect/logout";

        // Redirect to the Keycloak logout URL
        response.sendRedirect(keycloakLogoutUrl);
    }
}
