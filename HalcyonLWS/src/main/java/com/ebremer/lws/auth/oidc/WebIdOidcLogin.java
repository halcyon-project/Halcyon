package com.ebremer.lws.auth.oidc;

import com.ebremer.lws.auth.PresentedToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
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
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import org.apache.jena.rdf.model.Model;

/**
 * Interactive WebID login (the browser relying-party flow): a person types a WebID, and this
 * discovers <em>their</em> OpenID Provider from the WebID's controlled identifier document, runs
 * an OpenID Connect Authorization-Code + PKCE login against that OP, and — on the callback —
 * validates the returned ID Token and confirms it authenticates that exact WebID.
 *
 * <p>This is the login counterpart to {@link LwsOidcVerifier} (which verifies a <em>presented</em>
 * credential). It reuses the same primitives: {@link CidResolver} to find the OP from the WebID and
 * {@link OidcKeys} to validate the ID Token's signature. The interactive Authorization-Code
 * mechanics (PKCE, state, nonce, the code exchange) are OAuth's, not LWS's — see PLAN.md. The
 * storage server must be a registered client at the discovered OP (a `client_id` + this
 * `redirectUri`); obtaining that registration is out of scope here.
 *
 * <p>Stateless: {@link #begin} returns a {@link Pending} the caller stashes server-side (the HTTP
 * session) and hands back to {@link #complete} on the callback. No signing algorithm is trusted
 * before verification, {@code state} defends the callback against CSRF, {@code nonce} against ID
 * Token replay, and the flow refuses unless the ID Token's {@code sub} equals the typed WebID.
 */
public final class WebIdOidcLogin {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final long SKEW_SECONDS = 60;

    /** A login attempt could not be started or completed. */
    public static final class WebIdLoginException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public WebIdLoginException(String message) {
            super(message);
        }
    }

    /** Server-side state for one in-flight login; stash between {@link #begin} and {@link #complete}. */
    public record Pending(String state, String nonce, String codeVerifier, String webId,
            String issuer, String tokenEndpoint) {
    }

    /** The browser redirect to the OP, plus the {@link Pending} to keep for the callback. */
    public record Redirect(String authorizationUrl, Pending pending) {
    }

    private final String clientId;
    private final String redirectUri;
    private final Set<String> allowedHosts;
    private final CidResolver cids;
    private final OidcKeys keys;
    private final HttpClient http;
    private final Duration timeout;
    private final SecureRandom random = new SecureRandom();

    public WebIdOidcLogin(String clientId, String redirectUri, Set<String> allowedHosts) {
        this(clientId, redirectUri, allowedHosts, new CidResolver(), new OidcKeys(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NEVER).build(),
                DEFAULT_TIMEOUT);
    }

    WebIdOidcLogin(String clientId, String redirectUri, Set<String> allowedHosts,
            CidResolver cids, OidcKeys keys, HttpClient http, Duration timeout) {
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.allowedHosts = allowedHosts;
        this.cids = cids;
        this.keys = keys;
        this.http = http;
        this.timeout = timeout;
    }

    /**
     * Start a login for {@code webId}: dereference it, discover its OP, and build the
     * Authorization-Code + PKCE redirect.
     *
     * @throws WebIdLoginException if the WebID is malformed, names no OpenID Provider, or discovery fails
     */
    public Redirect begin(String webId) {
        if (!isUrl(webId)) {
            throw new WebIdLoginException("not an absolute http(s) WebID: " + webId);
        }
        String issuer;
        try {
            Model cid = cids.dereference(webId, allowedHosts);
            issuer = cids.openIdProvider(cid, webId);
        } catch (SsrfGuard.BlockedException | CidResolver.CidException e) {
            throw new WebIdLoginException("could not resolve the WebID: " + e.getMessage());
        }
        if (issuer == null) {
            throw new WebIdLoginException("the WebID document names no OpenIdProvider: " + webId);
        }
        OidcDiscovery disc;
        try {
            disc = OidcDiscovery.fetch(issuer, http, timeout, allowedHosts);
        } catch (SsrfGuard.BlockedException | OidcKeys.OidcException e) {
            throw new WebIdLoginException("OpenID discovery failed for " + issuer + ": " + e.getMessage());
        }
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
        return new Redirect(url, new Pending(state, nonce, codeVerifier, webId, issuer, disc.tokenEndpoint()));
    }

    /**
     * Complete the callback: check {@code state}, exchange {@code code} for an ID Token, validate it
     * (signature via the OP's JWKS, issuer, nonce, expiry), and require its {@code sub} to be the
     * WebID this login was for.
     *
     * @return the authenticated WebID (equal to {@code pending.webId()})
     * @throws WebIdLoginException on any mismatch or validation failure
     */
    public String complete(Pending pending, String code, String returnedState) {
        if (pending == null) {
            throw new WebIdLoginException("no pending login for this callback");
        }
        if (returnedState == null || !constantTimeEquals(returnedState, pending.state())) {
            throw new WebIdLoginException("state mismatch (possible CSRF)");
        }
        if (code == null || code.isBlank()) {
            throw new WebIdLoginException("the callback carried no authorization code");
        }
        SsrfGuard.verify(pending.tokenEndpoint(), allowedHosts);
        String form = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri)
                + "&client_id=" + enc(clientId)
                + "&code_verifier=" + enc(pending.codeVerifier());
        JsonObject tokenResponse = postForm(pending.tokenEndpoint(), form);
        String idToken = tokenResponse.getString("id_token", null);
        if (idToken == null) {
            throw new WebIdLoginException("the token response contained no id_token");
        }

        PresentedToken parsed = PresentedToken.parse("Bearer " + idToken);
        if (parsed.alg() == null || "none".equalsIgnoreCase(parsed.alg())) {
            throw new WebIdLoginException("the id_token is not signed");
        }
        PublicKey key = keys.signingKey(pending.issuer(), parsed.kid(), parsed.alg(), allowedHosts);
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .clockSkewSeconds(SKEW_SECONDS)
                    .requireIssuer(pending.issuer())
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new WebIdLoginException("the id_token did not validate: " + e.getMessage());
        }
        String nonce = claims.get("nonce", String.class);
        if (nonce == null || !nonce.equals(pending.nonce())) {
            throw new WebIdLoginException("id_token nonce mismatch (possible replay)");
        }
        if (!pending.webId().equals(claims.getSubject())) {
            throw new WebIdLoginException("the id_token authenticates <" + claims.getSubject()
                    + ">, not the requested WebID <" + pending.webId() + ">");
        }
        if (claims.getExpiration() == null) {
            throw new WebIdLoginException("the id_token has no exp");
        }
        return pending.webId();
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
                throw new WebIdLoginException("the token endpoint returned HTTP " + resp.statusCode());
            }
            try (InputStream in = resp.body(); JsonReader r = Json.createReader(in)) {
                return r.readObject();
            }
        } catch (IOException e) {
            throw new WebIdLoginException("token exchange failed: " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebIdLoginException("interrupted during token exchange");
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
