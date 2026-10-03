package com.ebremer.lws.oauth;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The embedded authorization server's metadata (RFC 8414), at
 * {@value AuthorizationServerSettings#METADATA_PATH} as lws10-core §Authorization Server Metadata
 * requires.
 *
 * <p>This document is the whole of discovery: a client that has only a 401 challenge naming an
 * {@code as_uri} dereferences this to learn where to exchange a credential, which key set to verify
 * a token against, and which credentials this server will take. Nothing about the flow is
 * hardcoded on either side.
 *
 * <p>Beyond RFC 8414's members it carries the two lws10-core defines:
 *
 * <ul>
 *   <li>{@code subject_token_types_supported} — the {@code subject_token_type} values the token
 *       endpoint accepts, so a client with an ID Token knows it can use it here;</li>
 *   <li>{@code subject_identifier_types_supported} — {@code https}, for the WebIDs both suites
 *       dereference to a controlled identifier document, and with the CID suite
 *       ({@code SelfIssuedCidVerifier}) also {@code did:key} and {@code did:web}, the DID methods
 *       its verifier resolves. Stated rather than left to the {@code ["https"]} default.</li>
 * </ul>
 *
 * <p>Anonymous and cacheable: it names no agent, and a client needs it <em>before</em> it can
 * authenticate, so requiring a credential to read it would be circular.
 */
public final class AuthorizationServerMetadataServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final byte[] document;

    public AuthorizationServerMetadataServlet(AuthorizationServerSettings as,
            List<String> subjectTokenTypes) {
        JsonArrayBuilder tokenTypes = Json.createArrayBuilder();
        subjectTokenTypes.forEach(tokenTypes::add);
        this.document = Json.createObjectBuilder()
                .add("issuer", as.issuer())
                .add("token_endpoint", as.tokenEndpointUri())
                .add("jwks_uri", as.jwksUri())
                .add("grant_types_supported",
                        Json.createArrayBuilder().add(TokenExchange.GRANT_TYPE))
                .add("response_types_supported", Json.createArrayBuilder().add("token"))
                // A client is identified by the URI in its credential, not by a registration here,
                // so there is no client authentication to advertise.
                .add("token_endpoint_auth_methods_supported", Json.createArrayBuilder().add("none"))
                .add("claims_supported", Json.createArrayBuilder()
                        .add("iss").add("sub").add("client_id").add("aud")
                        .add("exp").add("iat").add("jti"))
                .add("subject_token_types_supported", tokenTypes)
                .add("subject_identifier_types_supported", identifierTypes(subjectTokenTypes))
                .add("id_token_signing_alg_values_supported",
                        Json.createArrayBuilder().add("ES256").add("RS256"))
                .build().toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code https} for the OpenID suite's WebIDs; with the CID suite (advertised by its token type,
     * {@code ...:jwt}) also the DID methods its verifier resolves, {@code did:key} and
     * {@code did:web}.
     */
    private static JsonArrayBuilder identifierTypes(List<String> subjectTokenTypes) {
        JsonArrayBuilder types = Json.createArrayBuilder().add("https");
        if (subjectTokenTypes.contains(TokenExchange.TYPE_JWT)) {
            types.add("did:key").add("did:web");
        }
        return types;
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
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setHeader("Cache-Control", "public, max-age=3600");
        resp.setContentLength(document.length);
        if ("GET".equals(method)) {
            resp.getOutputStream().write(document);
        }
    }
}
