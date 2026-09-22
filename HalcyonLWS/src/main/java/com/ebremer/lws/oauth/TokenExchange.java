package com.ebremer.lws.oauth;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.auth.CredentialVerifier;
import com.ebremer.lws.auth.InvalidBearerTokenException;
import com.ebremer.lws.auth.PresentedToken;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OAuth 2.0 Token Exchange (RFC 8693), as lws10-core §Authorization specifies it: a client presents
 * an authentication credential as the {@code subject_token} and a storage as the {@code resource},
 * and receives an RFC 9068 access token for that storage.
 *
 * <p>This is the piece that separates <em>who the agent is</em> from <em>what they may present to a
 * storage</em>. Before it, this module accepted an authentication credential — a Keycloak access
 * token, or an LWS OpenID ID Token — directly at a storage, which works but gives the credential
 * the storage's authority: a credential with no audience restriction, replayed at a second storage,
 * is accepted there too. An exchanged access token names exactly one storage in {@code aud} and
 * lives 300 seconds, so a captured one is worth very little and is worth nothing anywhere else.
 *
 * <p>What is checked, in order, because an error must not depend on how much of the request the
 * server bothered to read:
 *
 * <ul>
 *   <li>{@code grant_type} is the token-exchange grant, and nothing else;</li>
 *   <li>{@code resource} is REQUIRED and MUST identify a storage this server knows — an unknown or
 *       untrusted one is {@code invalid_target}, which is what stops this endpoint minting tokens
 *       for somebody else's storage;</li>
 *   <li>{@code requested_token_type}, if given, is an access token; delegation ({@code actor_token})
 *       is refused rather than silently ignored, since ignoring it would issue a token for the
 *       subject while the client asked to act on someone else's behalf;</li>
 *   <li>{@code subject_token} and {@code subject_token_type} are REQUIRED, and the credential is
 *       validated in full by the authentication suite the type names, before anything is issued;</li>
 *   <li>the credential's subject and client are URIs, since those become {@code sub} and
 *       {@code client_id}, which lws10-core requires to be URIs.</li>
 * </ul>
 *
 * <p>The issued token carries {@code iss}, {@code sub}, {@code client_id}, {@code aud},
 * {@code exp}, {@code iat} and {@code jti} — every claim lws10-core makes REQUIRED — and never
 * outlives the credential it was exchanged for: an expiring credential that could be traded for a
 * longer-lived token would defeat its own expiry.
 */
public final class TokenExchange {

    private static final Logger LOG = LoggerFactory.getLogger(TokenExchange.class);

    public static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
    public static final String TYPE_ID_TOKEN = "urn:ietf:params:oauth:token-type:id_token";
    public static final String TYPE_JWT = "urn:ietf:params:oauth:token-type:jwt";
    public static final String TYPE_ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token";

    /** The spelling lws10-core's own metadata example uses; accepted as {@link #TYPE_ID_TOKEN}. */
    private static final String TYPE_ID_TOKEN_HYPHENATED = "urn:ietf:params:oauth:token-type:id-token";

    /** An OAuth 2.0 error response (RFC 6749 §5.2). */
    public static final class OAuthError extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int status;
        private final String error;

        public OAuthError(int status, String error, String description) {
            super(description);
            this.status = status;
            this.error = error;
        }

        public int status() {
            return status;
        }

        public String error() {
            return error;
        }
    }

    /** An issued access token, as the token response reports it (RFC 6749 §5.1, RFC 8693 §2.2.1). */
    public record Issued(String accessToken, long expiresIn) {
    }

    private final AuthorizationServerSettings as;
    private final AccessTokenKeys keys;
    private final List<CredentialVerifier> suites;
    private final Clock clock;

    /**
     * @param suites the authentication suites that may validate a subject token, in the order they
     *               are consulted — the same verifiers the storages accept credentials with, so an
     *               identity that works one way works the other and there is no second definition
     *               of a valid credential to drift
     */
    public TokenExchange(AuthorizationServerSettings as, AccessTokenKeys keys,
            List<CredentialVerifier> suites, Clock clock) {
        this.as = as;
        this.keys = keys;
        this.suites = List.copyOf(suites);
        this.clock = clock;
    }

    /**
     * The subject token types this server will exchange, for its metadata.
     *
     * <p>Empty when no authentication suite is configured, in which case the token endpoint can
     * issue nothing and says so rather than advertising a capability it does not have.
     */
    public List<String> subjectTokenTypes() {
        if (suites.isEmpty()) {
            return List.of();
        }
        // Both name a signed JWT credential, and both are routed to the same suites: ...:id_token
        // is what lws10-authn-openid requires of an ID Token, and ...:jwt is the generic form.
        return List.of(TYPE_ID_TOKEN, TYPE_JWT);
    }

    /**
     * Exchange a credential for an access token.
     *
     * @param params the token request's parameters, each present at most once
     * @throws OAuthError for any request that cannot be honoured
     */
    public Issued exchange(Map<String, String> params, HttpServletRequest req) {
        String grantType = params.get("grant_type");
        if (grantType == null) {
            throw new OAuthError(400, "invalid_request", "grant_type is required");
        }
        if (!GRANT_TYPE.equals(grantType)) {
            throw new OAuthError(400, "unsupported_grant_type",
                    "this authorization server supports only " + GRANT_TYPE);
        }
        String resource = params.get("resource");
        if (resource == null || resource.isBlank()) {
            throw new OAuthError(400, "invalid_request",
                    "resource is required: the URI of the storage the token is for");
        }
        String realm = as.realmFor(resource);
        if (realm == null) {
            // "The authorization server MUST reject any request in which the resource parameter
            // identifies an unknown or untrusted storage."
            throw new OAuthError(400, "invalid_target",
                    "this authorization server issues tokens only for its own storages");
        }
        String audience = params.get("audience");
        if (audience != null && !realm.equals(as.realmFor(audience))) {
            throw new OAuthError(400, "invalid_target",
                    "audience, if given, must identify the same storage as resource");
        }
        String requested = params.get("requested_token_type");
        if (requested != null && !requested.equals(TYPE_ACCESS_TOKEN)) {
            throw new OAuthError(400, "invalid_request",
                    "only " + TYPE_ACCESS_TOKEN + " can be issued");
        }
        if (params.containsKey("actor_token")) {
            throw new OAuthError(400, "invalid_request", "delegation (actor_token) is not supported");
        }
        String subjectToken = params.get("subject_token");
        String subjectTokenType = params.get("subject_token_type");
        if (subjectToken == null || subjectToken.isBlank()) {
            throw new OAuthError(400, "invalid_request", "subject_token is required");
        }
        if (subjectTokenType == null || !subjectTokenTypes().contains(normalize(subjectTokenType))) {
            throw new OAuthError(400, "invalid_request",
                    "unsupported subject_token_type " + subjectTokenType);
        }

        AgentContext agent = validate(subjectToken, req);
        if (!isUri(agent.webId())) {
            throw new OAuthError(400, "invalid_request",
                    "the credential's subject is not a URI, so it cannot be the token's sub");
        }
        if (!isUri(agent.clientId())) {
            // lws10-authn-openid: "The ID Token MUST use the azp (authorized party) claim for the
            // LWS client identifier." Without it there is no client_id to put in the token, and
            // lws10-core makes client_id REQUIRED — inventing one would be a lie about who asked.
            throw new OAuthError(400, "invalid_request",
                    "the credential names no client, or its client is not a URI"
                            + " (an ID Token needs an azp claim)");
        }

        Instant now = clock.instant();
        Instant exp = now.plusSeconds(as.accessTokenLifetimeSeconds());
        Instant credentialExp = expiryOf(subjectToken);
        if (credentialExp != null && credentialExp.isBefore(exp)) {
            exp = credentialExp;
        }
        if (!exp.isAfter(now)) {
            throw new OAuthError(400, "invalid_request", "the subject_token has expired");
        }

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", as.issuer());
        claims.put("sub", agent.webId());
        claims.put("client_id", agent.clientId());
        claims.put("aud", realm);
        claims.put("jti", UUID.randomUUID().toString());
        String token = keys.sign(claims, Date.from(now), Date.from(exp));
        LOG.debug("issued an access token for {} on {} (client {})",
                agent.webId(), realm, agent.clientId());
        return new Issued(token, Math.max(1, exp.getEpochSecond() - now.getEpochSecond()));
    }

    /**
     * Validate the presented credential with the first suite that recognizes it.
     *
     * <p>A credential no suite claims, and one a suite claims and rejects, are the same answer to
     * the client: {@code invalid_request}, the status RFC 8693 gives for a subject token that does
     * not validate. Telling the two apart would say which issuers this server trusts.
     */
    private AgentContext validate(String subjectToken, HttpServletRequest req) {
        PresentedToken presented;
        try {
            presented = PresentedToken.parse("Bearer " + subjectToken);
        } catch (InvalidBearerTokenException e) {
            throw new OAuthError(400, "invalid_request", "the subject_token is not a JWT");
        }
        List<String> refusals = new ArrayList<>();
        for (CredentialVerifier suite : suites) {
            try {
                AgentContext agent = suite.tryAuthenticate(presented, req);
                if (agent != null && agent.isAuthenticated()) {
                    return agent;
                }
            } catch (InvalidBearerTokenException e) {
                // This suite recognized the credential and refused it. Another suite must not be
                // given the chance to accept it -- that would launder a tampered credential past
                // its own verifier's checks -- so remember the refusal and stop.
                refusals.add(e.getMessage());
                break;
            }
        }
        LOG.debug("refusing a token exchange: {}", refusals.isEmpty() ? "no suite claimed the "
                + "credential" : refusals);
        throw new OAuthError(400, "invalid_request",
                "the subject_token is not a valid authentication credential");
    }

    private static String normalize(String type) {
        return TYPE_ID_TOKEN_HYPHENATED.equals(type) ? TYPE_ID_TOKEN : type;
    }

    private static boolean isUri(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return URI.create(value).isAbsolute();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The credential's {@code exp}, read <em>without</em> verifying its signature.
     *
     * <p>Safe, because the credential has already been validated in full by a suite above — this is
     * not a trust decision but an arithmetic one, and it can only ever shorten the token being
     * issued. Reading the claim rather than re-parsing the JWT keeps it independent of which
     * signature algorithm the credential used.
     */
    private static Instant expiryOf(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
            try (var r = jakarta.json.Json.createReader(new java.io.ByteArrayInputStream(payload))) {
                jakarta.json.JsonObject o = r.readObject();
                return o.containsKey("exp")
                        ? Instant.ofEpochSecond(o.getJsonNumber("exp").longValue()) : null;
            }
        } catch (RuntimeException e) {
            return null;
        }
    }
}
