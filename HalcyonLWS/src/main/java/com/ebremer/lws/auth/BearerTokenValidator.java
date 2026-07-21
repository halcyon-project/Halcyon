package com.ebremer.lws.auth;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.http.Problem;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates the {@code Authorization: Bearer} access token and resolves the agent
 * for ONE LWS storage — the storage-shaped face of {@link BearerTokenVerifier},
 * which holds the actual checks (signature against the discovered JWKS, issuer,
 * temporal validity, and the mandatory audience-covers-this-storage rule; see
 * there). This class contributes what is LWS-specific: the storage's URI as the
 * protected resource, and the RFC 6750 challenge dressed as an LWS
 * {@link Problem} with the storage-description {@code Link}.
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

    public BearerTokenValidator(LwsStorageConfig cfg) {
        this.cfg = cfg;
        this.chain = CredentialChain.forResource(cfg.realm());
        LOG.info("LWS storage {} trusts issuer {} (resource {})",
                cfg.urlPath(), chain.authorizationServer(), cfg.realm());
    }

    /** The authorization server a client should go to for a token. */
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
     * The 401 challenge, which is how a client discovers where to get a token.
     *
     * <p>{@code as_uri} names the authorization server and {@code realm} the scope of
     * protection — both REQUIRED, and together they let a client authenticate without
     * a single hardcoded URI.
     *
     * <p>{@code error} is omitted when the request carried no credentials at all. RFC
     * 6750 is explicit: "If the request lacks any authentication information ... the
     * authorization server SHOULD NOT include an error code". An error code there would
     * be describing a failure that never happened — nothing was presented to reject.
     */
    public Problem unauthorized(String error, String detail) {
        StringBuilder challenge = new StringBuilder("Bearer as_uri=\"")
                .append(chain.authorizationServer())
                .append("\", realm=\"").append(cfg.realm()).append('"');
        if (error != null && !error.isBlank()) {
            challenge.append(", error=\"").append(error).append('"');
        }
        return Problem.unauthorized(detail)
                .header("WWW-Authenticate", challenge.toString())
                // Even on a 401, tell the client where the storage describes itself.
                .header("Link", "<" + cfg.descriptionUri() + ">; rel=\""
                        + com.ebremer.lws.vocab.LWS.REL_STORAGE_DESCRIPTION + "\"");
    }

    /** Challenge for a request that offered no credentials. */
    public Problem unauthenticated(String detail) {
        return unauthorized(null, detail);
    }
}
