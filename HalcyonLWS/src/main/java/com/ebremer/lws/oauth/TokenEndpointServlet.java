package com.ebremer.lws.oauth;

import jakarta.json.Json;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The embedded authorization server's token endpoint: {@code POST} a token-exchange request
 * (RFC 8693) as {@code application/x-www-form-urlencoded}, receive an access token for the storage
 * named in {@code resource}. {@link TokenExchange} holds what is checked and what is issued.
 *
 * <p>Clients are public. lws10-core identifies a client by the URI in its credential — the
 * {@code azp} of an ID Token — not by a registration here, so no client authentication is asked for
 * and the metadata advertises {@code none}. What confines an issued token is not who asked for it
 * but the credential presented and the {@code resource} it is scoped to.
 *
 * <p>Every response, success or error, is {@code application/json} with {@code Cache-Control:
 * no-store} (RFC 6749 §5.1): the body contains a bearer credential, and a cache that kept one would
 * hand it to the next caller.
 */
public final class TokenEndpointServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(TokenEndpointServlet.class);

    private static final String FORM = "application/x-www-form-urlencoded";

    private final transient TokenExchange exchange;

    public TokenEndpointServlet(TokenExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        switch (req.getMethod()) {
            case "POST" -> token(req, resp);
            case "OPTIONS" -> {
                resp.setHeader("Allow", "OPTIONS, POST");
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
            default -> {
                resp.setHeader("Allow", "OPTIONS, POST");
                error(resp, new TokenExchange.OAuthError(405, "invalid_request",
                        "a token request is a POST"));
            }
        }
    }

    private void token(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String contentType = req.getContentType();
            if (contentType == null || !bare(contentType).equals(FORM)) {
                throw new TokenExchange.OAuthError(400, "invalid_request",
                        "the request body must be " + FORM);
            }
            Map<String, String> params = new LinkedHashMap<>();
            for (Map.Entry<String, String[]> e : req.getParameterMap().entrySet()) {
                if (e.getValue().length != 1) {
                    // RFC 6749 §3.2: request parameters MUST NOT be included more than once.
                    // Accepting a duplicate would mean picking one, and which one is picked is
                    // exactly the ambiguity an attacker uses to smuggle a second resource past a
                    // proxy that validated the first.
                    throw new TokenExchange.OAuthError(400, "invalid_request",
                            e.getKey() + " is included more than once");
                }
                params.put(e.getKey(), e.getValue()[0]);
            }

            TokenExchange.Issued issued = exchange.exchange(params, req);

            byte[] body = Json.createObjectBuilder()
                    .add("access_token", issued.accessToken())
                    .add("issued_token_type", TokenExchange.TYPE_ACCESS_TOKEN)
                    .add("token_type", "Bearer")
                    .add("expires_in", issued.expiresIn())
                    .build().toString().getBytes(StandardCharsets.UTF_8);
            resp.setStatus(HttpServletResponse.SC_OK);
            noStore(resp);
            resp.setContentType("application/json");
            resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
            resp.setContentLength(body.length);
            resp.getOutputStream().write(body);
        } catch (TokenExchange.OAuthError e) {
            error(resp, e);
        } catch (RuntimeException e) {
            LOG.error("token request failed", e);
            error(resp, new TokenExchange.OAuthError(500, "server_error", "internal error"));
        }
    }

    /** RFC 6749 §5.2: {@code error} and, where it helps, {@code error_description}. */
    private static void error(HttpServletResponse resp, TokenExchange.OAuthError e)
            throws IOException {
        if (resp.isCommitted()) {
            return;
        }
        byte[] body = Json.createObjectBuilder()
                .add("error", e.error())
                .add("error_description", e.getMessage() == null ? "" : e.getMessage())
                .build().toString().getBytes(StandardCharsets.UTF_8);
        resp.setStatus(e.status());
        noStore(resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentLength(body.length);
        resp.getOutputStream().write(body);
    }

    private static void noStore(HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Pragma", "no-cache");
    }

    private static String bare(String contentType) {
        int semi = contentType.indexOf(';');
        return (semi < 0 ? contentType : contentType.substring(0, semi)).trim()
                .toLowerCase(java.util.Locale.ROOT);
    }
}
