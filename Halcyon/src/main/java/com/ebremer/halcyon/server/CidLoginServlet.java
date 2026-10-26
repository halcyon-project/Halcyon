package com.ebremer.halcyon.server;

import com.ebremer.lws.auth.oidc.CidOidcLogin;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code GET /cid-login} (and its deprecated alias {@code /webid-login}) — the interactive CID URL
 * login entry point (Option B).
 *
 * <p>With no {@code cid} parameter (or the deprecated {@code webid}) it serves a minimal form; with
 * one it discovers the OpenID Provider that CID document names, stashes the PKCE/state in the
 * session, and redirects the browser to the OP.
 * Anonymous by design (not in {@code URLControl.getSecuredURLs()}); off unless {@code lws-oidc.json}
 * enables it. The existing Keycloak login is untouched.
 */
public class CidLoginServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(CidLoginServlet.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!CidLogin.enabled()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "CID URL login is not enabled");
            return;
        }
        String cidUrl = request.getParameter("cid");
        if (cidUrl == null || cidUrl.isBlank()) {
            cidUrl = request.getParameter("webid"); // the pre-rename parameter, still accepted
        }
        if (cidUrl == null || cidUrl.isBlank()) {
            serveForm(response);
            return;
        }
        try {
            CidOidcLogin.Redirect redirect = CidLogin.flow().begin(cidUrl.trim());
            request.getSession(true).setAttribute(CidLogin.PENDING, redirect.pending());
            response.sendRedirect(redirect.authorizationUrl());
        } catch (CidOidcLogin.CidLoginException e) {
            logger.warn("CID URL login could not start for {}: {}", cidUrl, e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "CID URL login failed: " + e.getMessage());
        }
    }

    private static void serveForm(HttpServletResponse response) throws IOException {
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                <!doctype html><html><head><meta charset="utf-8"><title>Log in with your CID URL</title></head>
                <body style="font-family:sans-serif;max-width:40em;margin:3em auto">
                <h2>Log in with your CID URL</h2>
                <form method="get" action="/cid-login">
                  <input type="text" name="cid" size="50" autofocus
                         placeholder="https://you.example/profile#me"
                         style="padding:.5em;width:100%;box-sizing:border-box">
                  <p><button type="submit" style="padding:.5em 1em">Continue</button></p>
                </form>
                <p>You'll be sent to the OpenID Provider your CID document names, then back here.</p>
                </body></html>
                """);
    }
}
