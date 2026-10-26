package com.ebremer.halcyon.server;

import com.ebremer.lws.auth.oidc.CidOidcLogin;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code GET /cid-callback} (and its deprecated alias {@code /webid-callback}) — the OpenID
 * Provider redirects the browser here with the authorization code (Option B).
 *
 * <p>It validates {@code state}, exchanges the code, validates the returned ID Token (the OP's
 * signature, issuer, nonce, expiry, and that {@code sub} equals the CID URL), then stores the
 * authenticated CID URL in the session for {@code HalcyonSession} to seat as the principal (B3) and
 * redirects home. Anonymous route; off unless {@code lws-oidc.json} enables it.
 */
public class CidCallbackServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(CidCallbackServlet.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!CidLogin.enabled()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "CID URL login is not enabled");
            return;
        }
        String oauthError = request.getParameter("error");
        if (oauthError != null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "the OpenID Provider returned: " + oauthError);
            return;
        }
        HttpSession session = request.getSession(false);
        CidOidcLogin.Pending pending = session == null ? null
                : (CidOidcLogin.Pending) session.getAttribute(CidLogin.PENDING);
        if (pending == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "no CID URL login is in progress");
            return;
        }
        try {
            CidOidcLogin.Tokens tokens = CidLogin.flow().complete(pending,
                    request.getParameter("code"), request.getParameter("state"));
            // Rebuild the session so a fresh HalcyonSession seats the CID URL as the identity.
            // HalcyonSession reads this attribute only in its constructor (like the Keycloak path,
            // whose login link invalidates first), and an anonymous HalcyonSession may already exist
            // from before the login — reusing it would leave the user unauthenticated. Invalidating
            // also rotates the session id post-authentication (session-fixation defence).
            session.invalidate();
            HttpSession fresh = request.getSession(true);
            fresh.setAttribute(CidLogin.CID_URL, tokens.cidUrl());
            // Retain the tokens: the ID Token is a bare LWS-OIDC credential (sub == the CID URL) the GUI
            // presents to LWS storage as this CID URL, and the refresh token renews it on expiry (a CID URL
            // login has no Keycloak access token).
            fresh.setAttribute(CidLogin.TOKENS, tokens);
            logger.info("CID URL login succeeded for {}", tokens.cidUrl());
            response.sendRedirect("/");
        } catch (CidOidcLogin.CidLoginException e) {
            logger.warn("CID URL login callback failed: {}", e.getMessage());
            session.removeAttribute(CidLogin.PENDING);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "CID URL login failed: " + e.getMessage());
        }
    }
}
