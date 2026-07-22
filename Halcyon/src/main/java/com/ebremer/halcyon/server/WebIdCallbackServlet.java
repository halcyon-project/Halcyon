package com.ebremer.halcyon.server;

import com.ebremer.lws.auth.oidc.WebIdOidcLogin;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code GET /webid-callback} — the OpenID Provider redirects the browser here with the
 * authorization code (Option B).
 *
 * <p>It validates {@code state}, exchanges the code, validates the returned ID Token (the OP's
 * signature, issuer, nonce, expiry, and that {@code sub} equals the WebID), then stores the
 * authenticated WebID in the session for {@code HalcyonSession} to seat as the principal (B3) and
 * redirects home. Anonymous route; off unless {@code lws-oidc.json} enables it.
 */
public class WebIdCallbackServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(WebIdCallbackServlet.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!WebIdLogin.enabled()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "WebID login is not enabled");
            return;
        }
        String oauthError = request.getParameter("error");
        if (oauthError != null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "the OpenID Provider returned: " + oauthError);
            return;
        }
        HttpSession session = request.getSession(false);
        WebIdOidcLogin.Pending pending = session == null ? null
                : (WebIdOidcLogin.Pending) session.getAttribute(WebIdLogin.PENDING);
        if (pending == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "no WebID login is in progress");
            return;
        }
        try {
            WebIdOidcLogin.Tokens tokens = WebIdLogin.flow().complete(pending,
                    request.getParameter("code"), request.getParameter("state"));
            // Rebuild the session so a fresh HalcyonSession seats the WebID as the identity.
            // HalcyonSession reads this attribute only in its constructor (like the Keycloak path,
            // whose login link invalidates first), and an anonymous HalcyonSession may already exist
            // from before the login — reusing it would leave the user unauthenticated. Invalidating
            // also rotates the session id post-authentication (session-fixation defence).
            session.invalidate();
            HttpSession fresh = request.getSession(true);
            fresh.setAttribute(WebIdLogin.WEBID, tokens.webId());
            // Retain the tokens: the ID Token is a bare LWS-OIDC credential (sub == the WebID) the GUI
            // presents to LWS storage as this WebID, and the refresh token renews it on expiry (a WebID
            // login has no Keycloak access token).
            fresh.setAttribute(WebIdLogin.TOKENS, tokens);
            logger.info("WebID login succeeded for {}", tokens.webId());
            response.sendRedirect("/");
        } catch (WebIdOidcLogin.WebIdLoginException e) {
            logger.warn("WebID login callback failed: {}", e.getMessage());
            session.removeAttribute(WebIdLogin.PENDING);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "WebID login failed: " + e.getMessage());
        }
    }
}
