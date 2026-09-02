package com.ebremer.halcyon.server;

import com.ebremer.lws.auth.oidc.WebIdOidcLogin;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code GET /webid-login} — the interactive WebID login entry point (Option B).
 *
 * <p>With no {@code webid} parameter it serves a minimal form; with one it discovers that WebID's
 * OpenID Provider, stashes the PKCE/state in the session, and redirects the browser to the OP.
 * Anonymous by design (not in {@code URLControl.getSecuredURLs()}); off unless {@code lws-oidc.json}
 * enables it. The existing Keycloak login is untouched.
 */
public class WebIdLoginServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(WebIdLoginServlet.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!WebIdLogin.enabled()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "WebID login is not enabled");
            return;
        }
        String webid = request.getParameter("webid");
        if (webid == null || webid.isBlank()) {
            serveForm(response);
            return;
        }
        try {
            WebIdOidcLogin.Redirect redirect = WebIdLogin.flow().begin(webid.trim());
            request.getSession(true).setAttribute(WebIdLogin.PENDING, redirect.pending());
            response.sendRedirect(redirect.authorizationUrl());
        } catch (WebIdOidcLogin.WebIdLoginException e) {
            logger.warn("WebID login could not start for {}: {}", webid, e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "WebID login failed: " + e.getMessage());
        }
    }

    private static void serveForm(HttpServletResponse response) throws IOException {
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                <!doctype html><html><head><meta charset="utf-8"><title>Log in with your WebID</title></head>
                <body style="font-family:sans-serif;max-width:40em;margin:3em auto">
                <h2>Log in with your WebID</h2>
                <form method="get" action="/webid-login">
                  <input type="text" name="webid" size="50" autofocus
                         placeholder="https://you.example/profile#me"
                         style="padding:.5em;width:100%;box-sizing:border-box">
                  <p><button type="submit" style="padding:.5em 1em">Continue</button></p>
                </form>
                <p>You'll be sent to the OpenID Provider your WebID names, then back here.</p>
                </body></html>
                """);
    }
}
