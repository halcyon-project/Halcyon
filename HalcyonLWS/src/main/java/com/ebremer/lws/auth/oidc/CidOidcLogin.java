package com.ebremer.lws.auth.oidc;

import com.ebremer.lws.auth.PresentedToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interactive CID URL login (the browser relying-party flow): a person types a CID URL, and this
 * discovers <em>their</em> OpenID Provider from that controlled identifier document, runs an
 * OpenID Connect Authorization-Code + PKCE login against that OP, and — on the callback — validates
 * the returned ID Token and confirms it authenticates that exact CID URL.
 *
 * <p>The {@code client_id} is resolved per OP: either a configured, pre-registered id (for an OP you
 * arranged with), or — when dynamic registration is enabled — obtained at login time via
 * {@link DynamicClientRegistrar} (RFC 7591), so the login works with <em>any</em> conformant OP with
 * no pre-arrangement. The resolved id is carried in the {@link Pending} so {@link #complete} exchanges
 * the code as the same client.
 *
 * <p>This is the login counterpart to {@link LwsOidcVerifier} (which verifies a <em>presented</em>
 * credential), reusing {@link CidResolver} and {@link OidcKeys}. The Authorization-Code mechanics are
 * OAuth's, not LWS's — see PLAN.md. {@code state} defends the callback against CSRF, {@code nonce}
 * against ID Token replay, and the flow refuses unless the ID Token asserts the typed CID URL as its
 * {@code sub} or a {@code webid} claim.
 */
public final class CidOidcLogin {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final long SKEW_SECONDS = 60;
    /** Refresh the ID Token once it is within this many seconds of expiry. */
    private static final long REFRESH_SKEW_SECONDS = 30;
    private static final Logger LOG = LoggerFactory.getLogger(CidOidcLogin.class);

    /** A login attempt could not be started or completed. */
    public static final class CidLoginException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public CidLoginException(String message) {
            super(message);
        }
    }

    /** Server-side state for one in-flight login; stash between {@link #begin} and {@link #complete}. */
    public record Pending(String state, String nonce, String codeVerifier, String cidUrl,
            String issuer, String tokenEndpoint, String clientId) implements java.io.Serializable {
    }

    /** The browser redirect to the OP, plus the {@link Pending} to keep for the callback. */
    public record Redirect(String authorizationUrl, Pending pending) {
    }

    /**
     * The tokens from a completed login: the authenticated CID URL, the validated ID Token (itself a
     * bare LWS-OIDC credential whose {@code sub} is the CID URL — presentable to LWS storage as that
     * CID URL), the refresh token, and the OP coordinates needed to refresh. A browser session retains
     * this and calls {@link #freshTokens} to keep the presented ID Token current.
     */
    public record Tokens(String cidUrl, String idToken, String refreshToken, String issuer,
            String tokenEndpoint, String clientId) implements java.io.Serializable {
    }

    private final String configuredClientId;
    private final String redirectUri;
    private final boolean dynamicRegistration;
    private final Set<String> allowedHosts;
    private final CidResolver cids;
    private final OidcKeys keys;
    private final DynamicClientRegistrar registrar;
    private final HttpClient http;
    private final Duration timeout;
    private final SecureRandom random = new SecureRandom();
    private java.util.function.Supplier<TrustPolicy> issuers = () -> TrustPolicy.ALLOW_ALL;
    private java.util.function.Supplier<TrustPolicy> cidHosts = () -> TrustPolicy.ALLOW_ALL;

    public CidOidcLogin(String configuredClientId, String redirectUri, boolean dynamicRegistration,
            Set<String> allowedHosts) {
        this(configuredClientId, redirectUri, dynamicRegistration, allowedHosts,
                new CidResolver(), new OidcKeys(), new DynamicClientRegistrar(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NEVER).build(),
                DEFAULT_TIMEOUT);
    }

    /**
     * Apply this deployment's trust policies to the CID URL and the provider it nominates.
     *
     * <p>A setter rather than another constructor argument: this class already has a nine-argument
     * test seam, and the policies default to allow-all so every existing caller — including the
     * refresh path, whose issuer was already checked when the login began — behaves exactly as
     * before until a deployment configures something.
     *
     * @return this, for chaining at the call site
     */
    public CidOidcLogin withTrust(java.util.function.Supplier<TrustPolicy> issuers,
            java.util.function.Supplier<TrustPolicy> cidHosts) {
        this.issuers = issuers == null ? () -> TrustPolicy.ALLOW_ALL : issuers;
        this.cidHosts = cidHosts == null ? () -> TrustPolicy.ALLOW_ALL : cidHosts;
        return this;
    }

    private TrustPolicy issuers() {
        return issuers.get();
    }

    private TrustPolicy cidHosts() {
        return cidHosts.get();
    }

    CidOidcLogin(String configuredClientId, String redirectUri, boolean dynamicRegistration,
            Set<String> allowedHosts, CidResolver cids, OidcKeys keys, DynamicClientRegistrar registrar,
            HttpClient http, Duration timeout) {
        this.configuredClientId = configuredClientId;
        this.redirectUri = redirectUri;
        this.dynamicRegistration = dynamicRegistration;
        this.allowedHosts = allowedHosts;
        this.cids = cids;
        this.keys = keys;
        this.registrar = registrar;
        this.http = http;
        this.timeout = timeout;
    }

    /**
     * Start a login for {@code cidUrl}: dereference it, discover its OP, resolve a {@code client_id}
     * (configured or dynamically registered), and build the Authorization-Code + PKCE redirect.
     *
     * @throws CidLoginException if the CID URL is malformed, names no OpenID Provider, discovery
     *                            fails, or dynamic registration fails
     */
    public Redirect begin(String cidUrl) {
        if (!isUrl(cidUrl)) {
            throw new CidLoginException("not an absolute http(s) CID URL: " + cidUrl);
        }
        // Refuse a CID URL this deployment does not accept before dereferencing it, so an
        // unacceptable identifier costs no outbound request. Unset means allow all.
        try {
            cidHosts().require("CID host", cidUrl);
        } catch (TrustPolicy.RefusedException e) {
            throw new CidLoginException(e.getMessage());
        }
        String issuer;
        try {
            Model cid = cids.dereference(cidUrl, allowedHosts);
            issuer = cids.openIdProvider(cid, cidUrl);
        } catch (SsrfGuard.BlockedException | CidResolver.CidException e) {
            throw new CidLoginException("could not resolve the CID document: " + e.getMessage());
        }
        if (issuer == null) {
            throw new CidLoginException("the CID document names no OpenIdProvider: " + cidUrl);
        }
        // The provider is named BY the CID document, so it is the caller's choice, not ours.
        // This is where the deployment gets a say -- and it is checked before OIDC discovery, so a
        // provider we do not accept is never contacted.
        try {
            issuers().require("OpenID Provider", issuer);
        } catch (TrustPolicy.RefusedException e) {
            throw new CidLoginException(e.getMessage());
        }
        OidcDiscovery disc;
        try {
            disc = OidcDiscovery.fetch(issuer, http, timeout, allowedHosts);
        } catch (SsrfGuard.BlockedException | OidcKeys.OidcException e) {
            throw new CidLoginException("OpenID discovery failed for " + issuer + ": " + e.getMessage());
        }
        String clientId = resolveClientId(issuer, disc);

        String state = randomToken();
        String nonce = randomToken();
        String codeVerifier = randomToken();
        String url = disc.authorizationEndpoint()
                + (disc.authorizationEndpoint().contains("?") ? "&" : "?")
                + "response_type=code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri)
                + "&scope=" + enc("openid")
                + "&state=" + enc(state)
                + "&nonce=" + enc(nonce)
                + "&code_challenge=" + enc(challenge(codeVerifier))
                + "&code_challenge_method=S256";
        return new Redirect(url, new Pending(state, nonce, codeVerifier, cidUrl, issuer,
                disc.tokenEndpoint(), clientId));
    }

    private String resolveClientId(String issuer, OidcDiscovery disc) {
        if (dynamicRegistration) {
            try {
                return registrar.clientId(issuer, disc.registrationEndpoint(), redirectUri,
                        allowedHosts, http, timeout);
            } catch (SsrfGuard.BlockedException | DynamicClientRegistrar.RegistrationException e) {
                throw new CidLoginException("dynamic client registration failed at " + issuer
                        + ": " + e.getMessage());
            }
        }
        if (configuredClientId == null || configuredClientId.isBlank()) {
            throw new CidLoginException("no client_id configured and dynamic registration is off");
        }
        return configuredClientId;
    }

    /**
     * Complete the callback: check {@code state}, exchange {@code code} for an ID Token, validate it
     * (signature via the OP's JWKS, issuer, nonce, expiry), and require it to assert the CID URL this
     * login was for — as its {@code sub} or a {@code webid} claim.
     *
     * @return the authenticated CID URL plus the validated ID Token and refresh token (see {@link Tokens})
     * @throws CidLoginException on any mismatch or validation failure
     */
    public Tokens complete(Pending pending, String code, String returnedState) {
        if (pending == null) {
            throw new CidLoginException("no pending login for this callback");
        }
        if (returnedState == null || !constantTimeEquals(returnedState, pending.state())) {
            throw new CidLoginException("state mismatch (possible CSRF)");
        }
        if (code == null || code.isBlank()) {
            throw new CidLoginException("the callback carried no authorization code");
        }
        SsrfGuard.verify(pending.tokenEndpoint(), allowedHosts);
        String form = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri)
                + "&client_id=" + enc(pending.clientId())
                + "&code_verifier=" + enc(pending.codeVerifier());
        JsonObject tokenResponse = postForm(pending.tokenEndpoint(), form);
        String idToken = tokenResponse.getString("id_token", null);
        if (idToken == null) {
            throw new CidLoginException("the token response contained no id_token");
        }
        Claims claims = validateIdToken(idToken, pending.issuer(), pending.cidUrl());
        String nonce = claims.get("nonce", String.class);
        if (nonce == null || !nonce.equals(pending.nonce())) {
            throw new CidLoginException("id_token nonce mismatch (possible replay)");
        }
        String refreshToken = tokenResponse.getString("refresh_token", null);
        return new Tokens(pending.cidUrl(), idToken, refreshToken, pending.issuer(),
                pending.tokenEndpoint(), pending.clientId());
    }

    /**
     * Refresh the ID Token with the stored refresh token (RFC 6749 §6). The new ID Token is validated
     * exactly as a login one — signature via the OP's JWKS, issuer, expiry and the CID URL binding —
     * minus the one-time {@code nonce}. Returns the new {@link Tokens} (rotated refresh token kept).
     *
     * @throws CidLoginException if there is no refresh token, the OP refuses, or validation fails
     */
    public Tokens refresh(Tokens current) {
        if (current.refreshToken() == null || current.refreshToken().isBlank()) {
            throw new CidLoginException("no refresh token for " + current.cidUrl());
        }
        SsrfGuard.verify(current.tokenEndpoint(), allowedHosts);
        String form = "grant_type=refresh_token"
                + "&refresh_token=" + enc(current.refreshToken())
                + "&client_id=" + enc(current.clientId())
                + "&scope=" + enc("openid");
        JsonObject tokenResponse = postForm(current.tokenEndpoint(), form);
        String idToken = tokenResponse.getString("id_token", null);
        if (idToken == null) {
            throw new CidLoginException("the refresh response contained no id_token");
        }
        validateIdToken(idToken, current.issuer(), current.cidUrl());
        // Keycloak rotates the refresh token on use; keep the new one (fall back to the old if absent).
        String refreshToken = tokenResponse.getString("refresh_token", current.refreshToken());
        return new Tokens(current.cidUrl(), idToken, refreshToken, current.issuer(),
                current.tokenEndpoint(), current.clientId());
    }

    /**
     * {@code current} if its ID Token is still valid, otherwise a refreshed copy. On any refresh
     * failure it returns {@code current} unchanged — the stale token then 401s at storage, prompting
     * re-login. Static so a retained {@link Tokens} can be refreshed without holding the originating
     * {@link CidOidcLogin} (it needs no login-only state: no redirect URI, no PKCE, no nonce).
     */
    public static Tokens freshTokens(Tokens current, Set<String> allowedHosts) {
        if (current == null || current.idToken() == null) {
            return current;
        }
        long exp = expiryEpochSeconds(current.idToken());
        if (exp > 0 && exp - REFRESH_SKEW_SECONDS > Instant.now().getEpochSecond()) {
            return current;
        }
        if (current.refreshToken() == null || current.refreshToken().isBlank()) {
            return current;
        }
        try {
            return new CidOidcLogin(current.clientId(), null, false, allowedHosts).refresh(current);
        } catch (RuntimeException e) {
            LOG.warn("CID URL login token refresh failed for {}: {}", current.cidUrl(), e.getMessage());
            return current;
        }
    }

    /** Validate an ID Token as this login's CID URL credential: signed, JWKS-verified, issuer and expiry
     *  correct, and asserting the CID URL as {@code sub} or a {@code webid} claim. */
    private Claims validateIdToken(String idToken, String issuer, String cidUrl) {
        PresentedToken parsed = PresentedToken.parse("Bearer " + idToken);
        if (parsed.alg() == null || "none".equalsIgnoreCase(parsed.alg())) {
            throw new CidLoginException("the id_token is not signed");
        }
        PublicKey key = keys.signingKey(issuer, parsed.kid(), parsed.alg(), allowedHosts);
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .clockSkewSeconds(SKEW_SECONDS)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new CidLoginException("the id_token did not validate: " + e.getMessage());
        }
        // The OP must assert the typed CID URL — as sub (the LWS credential shape) or a webid claim.
        boolean bound = cidUrl.equals(claims.getSubject())
                || cidUrl.equals(claims.get("webid", String.class));
        if (!bound) {
            throw new CidLoginException("the id_token does not assert the requested CID URL <"
                    + cidUrl + "> (sub=" + claims.getSubject() + ")");
        }
        if (claims.getExpiration() == null) {
            throw new CidLoginException("the id_token has no exp");
        }
        return claims;
    }

    /** The ID Token's {@code exp} (epoch seconds), read WITHOUT verifying — only a heuristic for when
     *  to refresh; the refreshed token is fully validated, and storage validates whatever is presented. */
    private static long expiryEpochSeconds(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                return 0;
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            try (JsonReader r = Json.createReader(new ByteArrayInputStream(payload))) {
                JsonObject o = r.readObject();
                return o.containsKey("exp") ? o.getJsonNumber("exp").longValue() : 0;
            }
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private JsonObject postForm(String url, String form) {
        try {
            HttpResponse<InputStream> resp = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("Accept", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(form))
                            .build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                throw new CidLoginException("the token endpoint returned HTTP " + resp.statusCode());
            }
            try (InputStream in = resp.body(); JsonReader r = Json.createReader(in)) {
                return r.readObject();
            }
        } catch (IOException e) {
            throw new CidLoginException("token exchange failed: " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CidLoginException("interrupted during token exchange");
        }
    }

    private String randomToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String challenge(String codeVerifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isUrl(String s) {
        if (s == null) {
            return false;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }
}
