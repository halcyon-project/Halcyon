package com.ebremer.halcyon.fuseki.shiro;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.Jwts;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import java.security.Key;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies a Keycloak-issued JWS and returns its claims, or {@code null} if the
 * token is not trustworthy (caller must treat null as reject).
 * <p>
 * M4: the verification key is now selected by the JWS header's {@code kid} via
 * {@link KeycloakPublicKeyFetcher}. Previously the caller passed in one key that
 * the fetcher had picked by "last RS256 in the JWKS wins", so any token signed
 * with a different realm key — which is exactly what happens across a Keycloak
 * key rotation — failed to verify until the process was restarted.
 *
 * @author erich
 */
public class JwtVerifier {

    private static final Logger logger = LoggerFactory.getLogger(JwtVerifier.class);

    /** OIDC "authorized party" — the client the token was minted for. */
    private static final String AZP = "azp";

    /** Resolves the signing key named by the token's {@code kid}. */
    private static final Locator<Key> KEY_LOCATOR = new LocatorAdapter<Key>() {
        @Override
        protected Key locate(JwsHeader header) {
            return KeycloakPublicKeyFetcher.getKeycloakPublicKeyFetcher().resolve(header.getKeyId());
        }
    };

    public JwtVerifier() {}

    /**
     * @return the verified claims, or {@code null} when the token is expired,
     *         malformed, signed by an unknown key, issued by another realm,
     *         minted for another client, or missing a required claim. Callers
     *         MUST treat null as reject.
     */
    public Claims verify(String token) {
        // H2: the realm's authoritative issuer. If it cannot be resolved we do
        // not know who we trust, so we reject rather than skip the check.
        String issuer = KeycloakPublicKeyFetcher.getKeycloakPublicKeyFetcher().getIssuer();
        if (issuer == null) {
            logger.error("Rejecting JWT: the realm's issuer is unknown (OIDC discovery unavailable)");
            return null;
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .clockSkewSeconds(30) // Allow some clock skew
                    .keyLocator(KEY_LOCATOR)
                    .requireIssuer(issuer)   // H2: was unchecked
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            logger.warn("JWT expired: {}", ex.getMessage());
            return null;
        } catch (JwtException | IllegalArgumentException ex) {
            // Covers bad signature, unknown kid (locator returned null), wrong
            // issuer, malformed/unsupported tokens — all rejected the same way.
            logger.warn("JWT rejected: {}", ex.getMessage());
            return null;
        }
        if (!isForThisClient(claims)) {
            logger.warn("Rejecting JWT: azp/aud names another client (azp={}, aud={}), expected {}",
                    claims.get(AZP), claims.getAudience(), HalcyonSettings.CLIENT_ID);
            return null;
        }
        if (!hasRequiredClaims(claims)) {
            logger.warn("Rejecting JWT: missing required claim (sub/preferred_username)");
            return null;
        }
        return claims;
    }

    /**
     * H2: the token must have been minted for THIS application's client.
     * <p>
     * Every client of the realm is signed by the same realm key, so signature +
     * expiry alone accepted a token issued to <em>any</em> other client. Keycloak
     * stamps {@code azp} with the client that requested the token, so that is the
     * authoritative check; {@code aud} is accepted as a fallback for tokens that
     * carry no {@code azp}.
     */
    private static boolean isForThisClient(Claims claims) {
        String azp = claims.get(AZP, String.class);
        if (azp != null && !azp.isBlank()) {
            return HalcyonSettings.CLIENT_ID.equals(azp);
        }
        Set<String> audience = claims.getAudience();
        return audience != null && audience.contains(HalcyonSettings.CLIENT_ID);
    }

    /**
     * H2: the principal is built from these, so refuse a token that lacks them
     * rather than letting it through to fail later. A missing
     * {@code preferred_username} in particular used to collapse every such user
     * onto the SAME {@code <host>/user/} ACL identity (see L7).
     */
    private static boolean hasRequiredClaims(Claims claims) {
        return notBlank(claims.getSubject())
            && notBlank(claims.get("preferred_username", String.class));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
