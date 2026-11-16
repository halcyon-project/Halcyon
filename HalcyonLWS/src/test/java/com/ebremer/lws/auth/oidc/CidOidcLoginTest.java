package com.ebremer.lws.auth.oidc;

import com.ebremer.lws.auth.oidc.CidOidcLogin.Redirect;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CidOidcLogin} end to end against a loopback OP: begin() discovers the OP from the CID URL
 * and builds a PKCE Authorization-Code redirect; complete() exchanges the code and validates the
 * ID Token — accepting only when state, nonce and (crucially) the CID URL binding all hold. Also
 * covers RFC 7591 dynamic client registration: with no configured client id, begin() self-registers
 * at the OP's registration endpoint and drives the flow as the returned client, caching per issuer.
 */
class CidOidcLoginTest {

    private static final String KID = "k1";
    private static final KeyPair RSA = genRsa();
    private static final String REDIRECT = "https://storage.example/cid-callback";

    private final CidOidcLogin login = new CidOidcLogin(
            "halcyon-local", REDIRECT, false, Set.of("127.0.0.1"));

    private HttpServer server;
    private String base;
    private final AtomicReference<String> tokenBody = new AtomicReference<>("{}");
    private final AtomicInteger registerHits = new AtomicInteger();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void beginBuildsAPkceAuthorizationRedirect() throws IOException {
        String cidUrl = start(true);
        Redirect r = login.begin(cidUrl);

        String url = r.authorizationUrl();
        assertTrue(url.startsWith(base + "/authorize?"), url);
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("client_id=halcyon-local"));
        assertTrue(url.contains("scope=openid"));
        assertTrue(url.contains("code_challenge_method=S256"));
        assertTrue(url.contains("code_challenge="));
        assertTrue(url.contains("state=") && url.contains("nonce="));

        assertEquals(base, r.pending().issuer());
        assertEquals(cidUrl, r.pending().cidUrl());
        assertEquals(base + "/token", r.pending().tokenEndpoint());
        assertEquals("halcyon-local", r.pending().clientId());
        assertNotNull(r.pending().codeVerifier());
    }

    @Test
    void completesToTheCidUrlOnAValidIdToken() throws IOException {
        String cidUrl = start(true);
        Redirect r = login.begin(cidUrl);
        String idToken = mint(cidUrl, r.pending().nonce());
        setTokenResponse(idToken);

        CidOidcLogin.Tokens result = login.complete(r.pending(), "the-code", r.pending().state());
        assertEquals(cidUrl, result.cidUrl());
        assertEquals(idToken, result.idToken(), "the validated id_token is returned for reuse");
    }

    @Test
    void rejectsAStateMismatch() throws IOException {
        String cidUrl = start(true);
        Redirect r = login.begin(cidUrl);
        assertThrows(CidOidcLogin.CidLoginException.class,
                () -> login.complete(r.pending(), "the-code", "not-the-state"));
    }

    @Test
    void rejectsANonceMismatch() throws IOException {
        String cidUrl = start(true);
        Redirect r = login.begin(cidUrl);
        setTokenResponse(mint(cidUrl, "a-different-nonce"));
        assertThrows(CidOidcLogin.CidLoginException.class,
                () -> login.complete(r.pending(), "the-code", r.pending().state()));
    }

    @Test
    void rejectsWhenTheIdTokenAuthenticatesADifferentSubject() throws IOException {
        String cidUrl = start(true);
        Redirect r = login.begin(cidUrl);
        setTokenResponse(mint("https://someone-else.example/#me", r.pending().nonce()));
        assertThrows(CidOidcLogin.CidLoginException.class,
                () -> login.complete(r.pending(), "the-code", r.pending().state()));
    }

    @Test
    void acceptsAWebIdClaimWhenSubIsOpaque() throws IOException {
        String cidUrl = start(true);
        Redirect r = login.begin(cidUrl);
        // OP authenticates with an opaque sub but asserts the CID URL via a webid claim.
        setTokenResponse(mintWithWebid("9f77772d-e810-47d0-89ec-403b2d174493", cidUrl, r.pending().nonce()));

        assertEquals(cidUrl, login.complete(r.pending(), "the-code", r.pending().state()).cidUrl());
    }

    @Test
    void beginRefusesACidUrlThatNamesNoOpenIdProvider() throws IOException {
        String cidUrl = start(false);
        assertThrows(CidOidcLogin.CidLoginException.class, () -> login.begin(cidUrl));
    }

    @Test
    void dynamicallyRegistersThenDrivesTheFlowAsThatClient() throws IOException {
        String cidUrl = start(true);
        CidOidcLogin dyn = new CidOidcLogin(null, REDIRECT, true, Set.of("127.0.0.1"));

        Redirect r = dyn.begin(cidUrl);
        assertEquals(1, registerHits.get());
        assertTrue(r.authorizationUrl().contains("client_id=dyn-client-1"), r.authorizationUrl());
        assertEquals("dyn-client-1", r.pending().clientId());

        setTokenResponse(mint(cidUrl, r.pending().nonce()));
        assertEquals(cidUrl, dyn.complete(r.pending(), "the-code", r.pending().state()).cidUrl());
    }

    @Test
    void cachesTheDynamicClientIdAcrossLogins() throws IOException {
        String cidUrl = start(true);
        CidOidcLogin dyn = new CidOidcLogin(null, REDIRECT, true, Set.of("127.0.0.1"));

        dyn.begin(cidUrl);
        dyn.begin(cidUrl);
        assertEquals(1, registerHits.get());
    }

    @Test
    void freshTokensRefreshesAnExpiredIdToken() throws IOException {
        String cidUrl = start(true);
        // A retained session whose id_token has expired, but with a refresh token to renew it.
        CidOidcLogin.Tokens stale = new CidOidcLogin.Tokens(
                cidUrl, mintExpired(cidUrl), "the-refresh-token", base, base + "/token", "halcyon-local");
        String renewed = mint(cidUrl, "no-nonce-on-refresh");
        setTokenResponse(renewed);

        CidOidcLogin.Tokens fresh = CidOidcLogin.freshTokens(stale, Set.of("127.0.0.1"));
        assertEquals(renewed, fresh.idToken(), "the refreshed id_token replaces the expired one");
        assertEquals(cidUrl, fresh.cidUrl());
    }

    @Test
    void freshTokensReturnsAValidTokenUntouched() throws IOException {
        String cidUrl = start(true);
        CidOidcLogin.Tokens current = new CidOidcLogin.Tokens(
                cidUrl, mint(cidUrl, "n"), "the-refresh-token", base, base + "/token", "halcyon-local");
        // A still-valid id_token must not trigger a refresh (leave the token endpoint unset).
        assertEquals(current, CidOidcLogin.freshTokens(current, Set.of("127.0.0.1")));
    }

    // --- fixtures -------------------------------------------------------------------------

    private String mint(String sub, String nonce) {
        return Jwts.builder()
                .header().keyId(KID).and()
                .issuer(base).subject(sub).claim("nonce", nonce).claim("azp", "halcyon-local")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(RSA.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String mintExpired(String sub) {
        return Jwts.builder()
                .header().keyId(KID).and()
                .issuer(base).subject(sub)
                .expiration(Date.from(Instant.now().minusSeconds(120)))
                .signWith(RSA.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String mintWithWebid(String sub, String cidUrl, String nonce) {
        return Jwts.builder()
                .header().keyId(KID).and()
                .issuer(base).subject(sub).claim("webid", cidUrl).claim("nonce", nonce)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(RSA.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private void setTokenResponse(String idToken) {
        tokenBody.set("{\"token_type\":\"Bearer\",\"access_token\":\"x\",\"id_token\":\"" + idToken + "\"}");
    }

    private String start(boolean cidNamesProvider) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        String cidUrl = base + "/cid";
        String cid = cidNamesProvider
                ? "<" + cidUrl + "> <https://www.w3.org/ns/did#service> [ "
                        + "a <https://www.w3.org/ns/lws#OpenIdProvider> ; "
                        + "<https://www.w3.org/ns/did#serviceEndpoint> <" + base + "> ] ."
                : "<" + cidUrl + "> <http://xmlns.com/foaf/0.1/name> \"no provider\" .";
        String discovery = "{\"issuer\":\"" + base + "\",\"authorization_endpoint\":\"" + base + "/authorize\","
                + "\"token_endpoint\":\"" + base + "/token\",\"jwks_uri\":\"" + base + "/jwks\","
                + "\"registration_endpoint\":\"" + base + "/register\"}";
        String jwks = rsaJwks(KID, (RSAPublicKey) RSA.getPublic());
        server.createContext("/cid", ex -> respond(ex, cid, "text/turtle"));
        server.createContext("/.well-known/openid-configuration", ex -> respond(ex, discovery, "application/json"));
        server.createContext("/jwks", ex -> respond(ex, jwks, "application/json"));
        server.createContext("/token", ex -> respond(ex, tokenBody.get(), "application/json"));
        server.createContext("/register", ex -> {
            registerHits.incrementAndGet();
            respond(ex, "{\"client_id\":\"dyn-client-1\"}", "application/json");
        });
        server.start();
        return cidUrl;
    }

    private static void respond(HttpExchange ex, String body, String contentType) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private static String rsaJwks(String kid, RSAPublicKey k) {
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + kid + "\",\"n\":\""
                + uint(k.getModulus()) + "\",\"e\":\"" + uint(k.getPublicExponent()) + "\"}]}";
    }

    private static String uint(BigInteger bi) {
        byte[] b = bi.toByteArray();
        int i = 0;
        while (i < b.length - 1 && b[i] == 0) {
            i++;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOfRange(b, i, b.length));
    }

    private static KeyPair genRsa() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
