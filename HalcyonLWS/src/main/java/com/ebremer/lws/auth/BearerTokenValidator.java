package com.ebremer.lws.auth;

import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.http.Problem;
import com.ebremer.lws.oauth.ProtectedResourceMetadataServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates the {@code Authorization: Bearer} access token and resolves the agent
 * for ONE LWS storage — the storage-shaped face of {@link BearerTokenVerifier},
 * which holds the actual checks (signature against the discovered JWKS, issuer,
 * temporal validity, and the mandatory audience-covers-this-storage rule; see
 * there). This class contributes what is LWS-specific: the storage's URI as the
 * protected resource, and the RFC 6750 challenge dressed as an LWS
 * {@link Problem} with the storage {@code Link}.
 *
 * <p>The split exists so the MCP endpoint validates tokens with the SAME code —
 * the H2 lesson: a single definition cannot drift. Halcyon's old Fuseki
 * {@code JwtVerifier} validated only signature and expiry, and this module must
 * never grow a second verifier like it.
 */
public final class BearerTokenValidator {

    private static final Logger LOG = LoggerFactory.getLogger(BearerTokenValidator.class);

    private final LwsStorageConfig cfg;
    private final CredentialChain chain;
    private final boolean legacyChallenge;

    public BearerTokenValidator(LwsStorageConfig cfg) {
        this(cfg, CredentialChain.forResource(cfg.realm()), LwsSettings.get().legacyChallenge());
    }

    /**
     * @param legacyChallenge whether the challenge still carries {@code as_uri} and {@code realm}
     *                        ({@link LwsSettings#legacyChallenge()})
     */
    BearerTokenValidator(LwsStorageConfig cfg, CredentialChain chain, boolean legacyChallenge) {
        this.cfg = cfg;
        this.chain = chain;
        this.legacyChallenge = legacyChallenge;
        LOG.info("LWS storage {} trusts issuer {} (resource {})",
                cfg.urlPath(), chain.authorizationServer(), cfg.realm());
    }

    /**
     * The authorization server a client should go to for a token: the one this storage's protected
     * resource metadata names in {@code authorization_servers}.
     */
    public String authorizationServer() {
        return chain.authorizationServer();
    }

    /**
     * Resolve the requesting agent.
     *
     * <p>No {@code Authorization} header is not an error: LWS resources may be
     * public, and it is the <em>authorization</em> layer that decides. An
     * unauthenticated request becomes {@link AgentContext#PUBLIC} and is then
     * matched against ACP like any other. A malformed or invalid token, on the other
     * hand, is always a 401 — it is an assertion of identity that did not hold up.
     */
    public AgentContext authenticate(HttpServletRequest req) {
        try {
            return chain.authenticate(req);
        } catch (InvalidBearerTokenException e) {
            throw unauthorized(e.error(), e.getMessage());
        }
    }

    /**
     * The 401, which is how a client discovers where to get a token, without a single hardcoded URI.
     *
     * <p>lws10-core §Authorization Server Discovery (since w3c/lws-protocol#256): the response "MUST
     * send a {@code WWW-Authenticate} header field containing at least one conforming challenge. A
     * conforming challenge MUST include the {@code resource_metadata} parameter defined in Section
     * 5.1 of [RFC9728], whose value is the URL of the protected resource metadata document for the
     * requested resource." It is that of the <em>request</em> — not of the storage root, nor of the
     * resource a handler was deciding about — because a client discards metadata whose
     * {@code resource} is not identical to the URL it requested; so the challenge is computed when the
     * problem is written, from the request it answers ({@link #challenge(HttpServletRequest, String)}).
     *
     * <p>"The response MUST also include a {@code Link} header identifying the storage that contains
     * the requested resource" — the URI the client then sends as the token request's
     * {@code resource}, and whose storage description it reads to check that the storage root is on
     * the origin it requested.
     *
     * <p>{@code error} is omitted when the request carried no credentials at all. RFC
     * 6750 is explicit: "If the request lacks any authentication information ... the
     * authorization server SHOULD NOT include an error code". An error code there would
     * be describing a failure that never happened — nothing was presented to reject.
     * When a presented token fails validation, lws10-core wants both: "the storage server MUST
     * return 401 Unauthorized with a {@code WWW-Authenticate} header containing the
     * {@code resource_metadata} parameter and an appropriate error parameter".
     */
    public Problem unauthorized(String error, String detail) {
        return Problem.unauthorized(detail)
                .header("WWW-Authenticate", req -> challenge(req, error))
                .header("Link", "<" + cfg.storageRootUri() + ">; rel=\""
                        + com.ebremer.lws.vocab.LWS.REL_STORAGE + "\"");
    }

    /** Challenge for a request that offered no credentials. */
    public Problem unauthenticated(String detail) {
        return unauthorized(null, detail);
    }

    /** The challenge refusing {@code req}: its {@code resource_metadata} is that of the URL requested. */
    String challenge(HttpServletRequest req, String error) {
        return challenge(ProtectedResourceMetadataServlet.metadataUrl(cfg.siteUrl(),
                ProtectedResourceMetadataServlet.requestPath(req), req.getQueryString()), error);
    }

    /**
     * The {@code Bearer} challenge, {@code resource_metadata} first.
     *
     * <p>After it, while {@code :LWSLegacyChallenge} is on (the default), come the two parameters the
     * drafts before 19 October 2026 required instead and the current one no longer mentions:
     * {@code as_uri}, the issuer of the authorization server, and {@code realm}, the storage's base
     * URI. lws10-core lets a challenge carry "other headers and parameters", and clients written
     * against those drafts still look for them.
     */
    String challenge(String resourceMetadata, String error) {
        List<String> params = new ArrayList<>();
        params.add(param("resource_metadata", resourceMetadata));
        if (legacyChallenge) {
            params.add(param("as_uri", chain.authorizationServer()));
            params.add(param("realm", cfg.realm()));
        }
        if (error != null && !error.isBlank()) {
            params.add(param("error", error));
        }
        return "Bearer " + String.join(", ", params);
    }

    /** An auth-param as an RFC 9110 quoted-string: backslash and double quote escaped, nothing else. */
    private static String param(String name, String value) {
        return name + "=\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
