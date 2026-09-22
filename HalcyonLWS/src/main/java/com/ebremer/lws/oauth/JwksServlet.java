package com.ebremer.lws.oauth;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The authorization server's {@code jwks_uri}: the public half of the access-token signing key, as a
 * JWK Set (RFC 7517).
 *
 * <p>lws10-core requires a storage server to verify an access token with "the authorization server's
 * public key retrieved from the jwks_uri specified in the authorization server metadata", and to
 * support key rotation. Publishing the key here is what makes that possible for a validator that is
 * <em>not</em> this process — a second Halcyon instance in front of the same storages, or an
 * external service reading the tokens. The storage in this process holds the same key directly and
 * does not fetch it (see {@code AccessTokenValidator}).
 *
 * <p>Anonymous, like the metadata that points at it: a verification key is public by construction,
 * and a validator needs it before it can trust anything.
 *
 * <p>The {@code max-age} is short relative to a key's life but long relative to a token's, so a
 * cache absorbs the load while a rotation still takes effect in minutes rather than hours.
 */
public final class JwksServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final byte[] document;

    public JwksServlet(AccessTokenKeys keys) {
        this.document = keys.jwkSet().toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String method = req.getMethod();
        if ("OPTIONS".equals(method)) {
            resp.setHeader("Allow", "OPTIONS, HEAD, GET");
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            resp.setHeader("Allow", "OPTIONS, HEAD, GET");
            resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/jwk-set+json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setHeader("Cache-Control", "public, max-age=300");
        resp.setContentLength(document.length);
        if ("GET".equals(method)) {
            resp.getOutputStream().write(document);
        }
    }
}
