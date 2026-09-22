package com.ebremer.lws.auth;

import com.ebremer.lws.oauth.AccessTokenKeys;
import com.ebremer.lws.oauth.AuthorizationServerSettings;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates an RFC 9068 access token issued by this deployment's authorization server, as
 * lws10-core §Token Validation by a Storage Server requires.
 *
 * <p>This is the baseline credential a storage accepts: an {@code at+jwt} whose {@code aud} names
 * exactly one storage, obtained by exchanging an authentication credential at the token endpoint.
 * It goes first in the {@link CredentialChain}, ahead of the suites that accept a credential
 * directly, so the baseline is what a request is measured against whenever one is presented.
 *
 * <p>The checks are lws10-core's, in its order:
 *
 * <ul>
 *   <li><b>Signature</b> against the authorization server's published verification key. The key is
 *       held directly here rather than fetched from {@code jwks_uri}: this instance <em>is</em> the
 *       authorization server, and fetching its own key over HTTP would add a network dependency
 *       with no added trust. Rotation is handled the same way either way — the {@code kid} is the
 *       key's thumbprint, so a rotated key simply stops verifying tokens minted by the old one, and
 *       those expire within minutes.</li>
 *   <li><b>Issuer</b> equals the expected authorization server.</li>
 *   <li><b>Audience</b> contains <em>exactly one</em> value, and that value is the storage
 *       logically containing the target resource. "Exactly one" is the spec's word and it matters:
 *       a token good for several storages is a token that one storage's compromise spreads.</li>
 *   <li><b>Temporal</b> — {@code exp} in the future, {@code nbf} not in the future, {@code iat} not
 *       in the future, each with a small clock skew.</li>
 * </ul>
 *
 * <p>And two the spec states as claim requirements rather than validation steps, enforced here
 * because a token missing either cannot be authorized against: {@code sub} must be a URI (it is the
 * agent ACP matches on) and {@code client_id} must be present (it is what an {@code acp:client}
 * matcher and an ODRL {@code client} constraint are evaluated against — absent, a policy naming a
 * client would silently match nothing rather than fail).
 *
 * <p>An algorithm confusion attack is refused before anything else: a token whose header says
 * {@code none} or an HMAC algorithm is rejected outright rather than handed to a parser along with
 * a public key.
 */
public final class AccessTokenValidator implements CredentialVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(AccessTokenValidator.class);

    private static final long SKEW_SECONDS = 60;

    /** Asymmetric signature algorithms only: a storage holds a verification key, never a signing one. */
    private static final Set<String> ALLOWED_ALGS =
            Set.of("ES256", "ES384", "ES512", "RS256", "RS384", "RS512", "PS256", "PS384", "PS512");

    private final AuthorizationServerSettings as;
    private final AccessTokenKeys keys;
    private final String realm;

    /**
     * @param realm the storage this validator guards — the one value an access token's {@code aud}
     *              may hold, and the same value the {@code WWW-Authenticate} challenge advertises
     */
    public AccessTokenValidator(AuthorizationServerSettings as, AccessTokenKeys keys, String realm) {
        this.as = as;
        this.keys = keys;
        this.realm = realm;
    }

    @Override
    public AgentContext tryAuthenticate(PresentedToken token, HttpServletRequest req) {
        if (!isAccessToken(token)) {
            return null; // not an access token from our authorization server; let a suite try
        }
        String alg = token.alg();
        if (alg == null || !ALLOWED_ALGS.contains(alg.toUpperCase(Locale.ROOT))) {
            throw new InvalidBearerTokenException("invalid_token",
                    "an access token must be signed with an asymmetric algorithm");
        }

        Claims claims;
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(keys.verificationKey())
                    .clockSkewSeconds(SKEW_SECONDS)
                    .requireIssuer(as.issuer())
                    .build()
                    .parseSignedClaims(token.raw());
            if (!AccessTokenKeys.AT_JWT.equals(jws.getHeader().getType())) {
                // Belt and braces: isAccessToken already routed on typ, but the header is only
                // trustworthy once the signature is verified, and RFC 9068's typ is what keeps an
                // ID Token from being replayed here as an access token.
                throw new InvalidBearerTokenException("invalid_token",
                        "an access token must carry typ " + AccessTokenKeys.AT_JWT);
            }
            claims = jws.getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            LOG.debug("rejecting an access token: {}", e.toString());
            throw new InvalidBearerTokenException("invalid_token", "the access token is not valid");
        }

        Set<String> audience = claims.getAudience();
        if (audience == null || audience.size() != 1) {
            throw new InvalidBearerTokenException("invalid_token",
                    "an access token's aud must contain exactly one value");
        }
        if (!realm.equals(audience.iterator().next())) {
            // The token is valid but was minted for a different storage. invalid_token, not
            // insufficient_scope: the credential does not apply here at all.
            throw new InvalidBearerTokenException("invalid_token",
                    "this access token was not issued for " + realm);
        }
        if (claims.getIssuedAt() == null) {
            throw new InvalidBearerTokenException("invalid_token", "an access token must carry iat");
        }
        if (claims.getExpiration() == null) {
            throw new InvalidBearerTokenException("invalid_token", "an access token must carry exp");
        }
        if (claims.getId() == null || claims.getId().isBlank()) {
            throw new InvalidBearerTokenException("invalid_token", "an access token must carry jti");
        }
        String sub = claims.getSubject();
        if (sub == null || !sub.contains(":")) {
            throw new InvalidBearerTokenException("invalid_token",
                    "an access token's sub must be a URI identifying the agent");
        }
        String clientId = claims.get("client_id", String.class);
        if (clientId == null || clientId.isBlank()) {
            throw new InvalidBearerTokenException("invalid_token",
                    "an access token must carry client_id");
        }
        return new AgentContext(sub, clientId, claims.getIssuer(), List.of());
    }

    /**
     * Whether this is an access token from our authorization server, decided on the token's
     * <em>unverified</em> header and {@code iss}.
     *
     * <p>Routing, not trust: whichever verifier a forged claim sends the token to will reject it.
     * What it buys is that an access token is never offered to an authentication suite, and a bare
     * authentication credential is never offered to this validator — so each failure message
     * describes the credential the client actually presented.
     */
    private boolean isAccessToken(PresentedToken token) {
        return AccessTokenKeys.AT_JWT.equals(token.typ())
                || as.issuer().equals(token.iss());
    }
}
