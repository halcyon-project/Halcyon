package com.ebremer.lws.auth.oidc;

import com.ebremer.lws.auth.oidc.WebIdOidcLogin.Redirect;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WebIdOidcLogin} end to end against a loopback OP: begin() discovers the OP from the WebID
 * and builds a PKCE Authorization-Code redirect; complete() exchanges the code and validates the
 * ID Token — accepting only when state, nonce and (crucially) {@code sub == the WebID} all hold.
 */
class WebIdOidcLoginTest {

    private static final String KID = "k1";
    private static final KeyPair RSA = genRsa();

    private final WebIdOidcLogin login = new WebIdOidcLogin(
            "halcyon-local", "https://storage.example/webid-callback", Set.of("127.0.0.1"));

    private HttpServer server;
    private String base;
    private final AtomicReference<String> tokenBody = new AtomicReference<>("{}");

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void beginBuildsAPkceAuthorizationRedirect() throws IOException {
        String webId = start(true);
        Redirect r = login.begin(webId);

        String url = r.authorizationUrl();
        assertTrue(url.startsWith(base + "/authorize?"), url);
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("client_id=halcyon-local"));
        assertTrue(url.contains("scope=openid"));
        assertTrue(url.contains("code_challenge_method=S256"));
        assertTrue(url.contains("code_challenge="));
        assertTrue(url.contains("state=") && url.contains("nonce="));

        assertEquals(base, r.pending().issuer());
        assertEquals(webId, r.pending().webId());
        assertEquals(base + "/token", r.pending().tokenEndpoint());
        assertNotNull(r.pending().codeVerifier());
    }

    @Test
    void completesToTheWebIdOnAValidIdToken() throws IOException {
        String webId = start(true);
        Redirect r = login.begin(webId);
        setTokenResponse(mint(webId, r.pending().nonce()));

        assertEquals(webId, login.complete(r.pending(), "the-code", r.pending().state()));
    }

    @Test
    void rejectsAStateMismatch() throws IOException {
        String webId = start(true);
        Redirect r = login.begin(webId);
        assertThrows(WebIdOidcLogin.WebIdLoginException.class,
                () -> login.complete(r.pending(), "the-code", "not-the-state"));
    }

    @Test
    void rejectsANonceMismatch() throws IOException {
        String webId = start(true);
        Redirect r = login.begin(webId);
        setTokenResponse(mint(webId, "a-different-nonce"));
        assertThrows(WebIdOidcLogin.WebIdLoginException.class,
                () -> login.complete(r.pending(), "the-code", r.pending().state()));
    }

    @Test
    void rejectsWhenTheIdTokenAuthenticatesADifferentSubject() throws IOException {
        String webId = start(true);
        Redirect r = login.begin(webId);
        setTokenResponse(mint("https://someone-else.example/#me", r.pending().nonce()));
        assertThrows(WebIdOidcLogin.WebIdLoginException.class,
                () -> login.complete(r.pending(), "the-code", r.pending().state()));
    }

    @Test
    void beginRefusesAWebIdThatNamesNoOpenIdProvider() throws IOException {
        String webId = start(false);
        assertThrows(WebIdOidcLogin.WebIdLoginException.class, () -> login.begin(webId));
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

    private void setTokenResponse(String idToken) {
        tokenBody.set("{\"token_type\":\"Bearer\",\"access_token\":\"x\",\"id_token\":\"" + idToken + "\"}");
    }

    private String start(boolean cidNamesProvider) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        String webId = base + "/webid";
        String cid = cidNamesProvider
                ? "<" + webId + "> <https://www.w3.org/ns/did#service> [ "
                        + "a <https://www.w3.org/ns/lws#OpenIdProvider> ; "
                        + "<https://www.w3.org/ns/did#serviceEndpoint> <" + base + "> ] ."
                : "<" + webId + "> <http://xmlns.com/foaf/0.1/name> \"no provider\" .";
        String discovery = "{\"issuer\":\"" + base + "\",\"authorization_endpoint\":\"" + base + "/authorize\","
                + "\"token_endpoint\":\"" + base + "/token\",\"jwks_uri\":\"" + base + "/jwks\"}";
        String jwks = rsaJwks(KID, (RSAPublicKey) RSA.getPublic());
        server.createContext("/webid", ex -> respond(ex, cid, "text/turtle"));
        server.createContext("/.well-known/openid-configuration", ex -> respond(ex, discovery, "application/json"));
        server.createContext("/jwks", ex -> respond(ex, jwks, "application/json"));
        server.createContext("/token", ex -> respond(ex, tokenBody.get(), "application/json"));
        server.start();
        return webId;
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
