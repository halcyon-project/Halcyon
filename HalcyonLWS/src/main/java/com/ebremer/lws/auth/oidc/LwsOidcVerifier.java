package com.ebremer.lws.auth.oidc;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.auth.CredentialVerifier;
import com.ebremer.lws.auth.InvalidBearerTokenException;
import com.ebremer.lws.auth.PresentedToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import java.security.PublicKey;
import java.util.List;
import java.util.Locale;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The LWS 1.0 OpenID Connect credential verifier (resource-server side): an ID Token whose
 * {@code sub} is a WebID, trusted by dereferencing that WebID to a controlled identifier
 * document (CID) that names the token's {@code iss} as its OpenID Provider.
 *
 * <p>This is the counterpart to Keycloak's own {@code /verify} endpoint — the same algorithm as
 * {@code com.ebremer.lws.authn.openid.verify.LWSCredentialVerifier} in the lws-authn extension,
 * re-expressed with Halcyon's stack (JJWT, {@code java.net.http}, Apache Jena):
 *
 * <ol>
 *   <li>the signing algorithm MUST NOT be {@code none};</li>
 *   <li>dereference {@code sub} to its CID (SSRF-guarded, normal TLS);</li>
 *   <li>the CID MUST declare {@code iss} as an {@code lws:OpenIdProvider} service for {@code sub};</li>
 *   <li>OpenID Connect Discovery on {@code iss}, then its JWKS, key pinned to the token's alg;</li>
 *   <li>verify the signature, the {@code iss} claim, and the active ({@code exp}) window.</li>
 * </ol>
 *
 * <p>It is <strong>not mine</strong> (returns {@code null}, so the chain moves on) unless the
 * token's {@code sub} is an absolute {@code http(s)} URL; once it is, any failure is a rejection.
 * Trust is established dynamically from the credential, so this verifier — unlike the Keycloak
 * one — accepts tokens from issuers it was never configured with. See {@code PLAN.md}, including
 * §5.3 on audience/replay: this suite defers audience confinement to the relying party, so a bare
 * bearer LWS credential is not audience-bound here.
 */
public final class LwsOidcVerifier implements CredentialVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(LwsOidcVerifier.class);
    private static final long SKEW_SECONDS = 60;

    private final LwsOidcSettings settings;
    private final java.util.function.Supplier<TrustPolicy> issuers;
    private final java.util.function.Supplier<TrustPolicy> webIdHosts;
    private final CidResolver cids;
    private final OidcKeys keys;

    /**
     * Uses this deployment's configured trust policies. The two-argument form exists so a test can
     * supply its own without a settings file on disk — the surrounding code passes trust
     * configuration as arguments ({@code CidResolver.dereference(sub, allowedHosts)}) rather than
     * reading ambient state, and this follows it.
     */
    public LwsOidcVerifier(LwsOidcSettings settings) {
        // Suppliers, not snapshots: the policies live in settings.ttl precisely so a hostile
        // identity provider can be revoked without a restart, and a value captured here would
        // outlive the reload that was supposed to remove it.
        this(settings, () -> com.ebremer.lws.config.LwsSettings.get().issuerPolicy(),
                () -> com.ebremer.lws.config.LwsSettings.get().webIdHostPolicy());
    }

    public LwsOidcVerifier(LwsOidcSettings settings,
            java.util.function.Supplier<TrustPolicy> issuers,
            java.util.function.Supplier<TrustPolicy> webIdHosts) {
        this(settings, new CidResolver(), new OidcKeys(), issuers, webIdHosts);
    }

    /**
     * Seam for tests: trust policies default to allow-all, which is what this class did before they
     * existed, so a test that does not care about them is unaffected and needs no settings file.
     */
    LwsOidcVerifier(LwsOidcSettings settings, CidResolver cids, OidcKeys keys) {
        this(settings, cids, keys, () -> TrustPolicy.ALLOW_ALL, () -> TrustPolicy.ALLOW_ALL);
    }

    LwsOidcVerifier(LwsOidcSettings settings, CidResolver cids, OidcKeys keys,
            java.util.function.Supplier<TrustPolicy> issuers,
            java.util.function.Supplier<TrustPolicy> webIdHosts) {
        this.settings = settings;
        this.cids = cids;
        this.keys = keys;
        this.issuers = issuers == null ? () -> TrustPolicy.ALLOW_ALL : issuers;
        this.webIdHosts = webIdHosts == null ? () -> TrustPolicy.ALLOW_ALL : webIdHosts;
    }

    @Override
    public AgentContext tryAuthenticate(PresentedToken token, HttpServletRequest req) {
        String sub = token.sub();
        if (!isUrl(sub)) {
            return null; // not an LWS-OIDC credential; let another verifier try
        }
        String iss = token.iss();
        if (!isUrl(iss)) {
            throw new InvalidBearerTokenException("invalid_token", "LWS credential has no dereferenceable issuer");
        }
        // 1. The credential MUST be signed.
        String alg = token.alg();
        if (alg == null || "none".equalsIgnoreCase(alg)) {
            throw new InvalidBearerTokenException("invalid_token", "LWS credential must be signed (alg is 'none')");
        }
        // 1b. This deployment's own view of who may vouch for a user. The CID check below
        // establishes that the subject NOMINATED this provider; it cannot establish that the
        // provider is one we accept, because the nomination comes from the credential itself.
        // Unset means allow all, so this is inert until an operator configures it. Checked before
        // any dereference, so a refused issuer costs no outbound request.
        try {
            issuers.get().require("OpenID Provider", iss);
            webIdHosts.get().require("WebID host", sub);
        } catch (TrustPolicy.RefusedException e) {
            LOG.debug("rejecting LWS credential for <{}>: {}", sub, e.getMessage());
            throw new InvalidBearerTokenException("invalid_token", e.getMessage());
        }

        try {
            // 2-3. Trust: the subject's CID must name iss as its OpenID Provider.
            Model cid = cids.dereference(sub, settings.allowedInternalHosts());
            if (!cids.declaresOpenIdProvider(cid, sub, iss)) {
                throw new InvalidBearerTokenException("invalid_token",
                        "the controlled identifier document for <" + sub + "> does not name <" + iss
                                + "> as its OpenID Provider");
            }
            // 4. Discover iss and resolve its signing key, pinned to the token's alg.
            PublicKey key = keys.signingKey(iss, token.kid(), alg, settings.allowedInternalHosts());

            // 5. Verify the signature, the issuer, and temporal validity.
            Claims claims;
            try {
                Jws<Claims> jws = Jwts.parser()
                        .verifyWith(key)
                        .clockSkewSeconds(SKEW_SECONDS)
                        .requireIssuer(iss)
                        .build()
                        .parseSignedClaims(token.raw());
                claims = jws.getPayload();
            } catch (JwtException | IllegalArgumentException e) {
                LOG.debug("LWS credential signature/claims invalid for <{}>: {}", sub, e.toString());
                throw new InvalidBearerTokenException("invalid_token", "the LWS credential is not valid");
            }

            // Defence in depth: the signature-covered claims must match what we dereferenced on.
            if (!sub.equals(claims.getSubject()) || !iss.equals(claims.getIssuer())) {
                throw new InvalidBearerTokenException("invalid_token", "LWS credential claims are inconsistent");
            }
            // Require an expiry so a captured credential is not replayable indefinitely.
            if (claims.getExpiration() == null) {
                throw new InvalidBearerTokenException("invalid_token", "the LWS credential is missing 'exp'");
            }

            String clientId = claims.get("azp", String.class) != null
                    ? claims.get("azp", String.class)
                    : claims.get("client_id", String.class);
            return new AgentContext(sub, clientId, iss, List.of());
        } catch (SsrfGuard.BlockedException | CidResolver.CidException | OidcKeys.OidcException e) {
            LOG.debug("rejecting LWS credential for <{}>: {}", sub, e.toString());
            throw new InvalidBearerTokenException("invalid_token", "the LWS credential could not be verified");
        }
    }

    /** An absolute http(s) URL — the shape of a WebID / issuer. */
    private static boolean isUrl(String s) {
        if (s == null) {
            return false;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }
}
