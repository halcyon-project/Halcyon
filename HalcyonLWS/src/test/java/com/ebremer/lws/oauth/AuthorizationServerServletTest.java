package com.ebremer.lws.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.auth.CredentialVerifier;
import com.ebremer.lws.store.LwsStore;
import io.jsonwebtoken.Jwts;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the three HTTP endpoints of the authorization server: the RFC 8414 metadata, the RFC 8693
 * token endpoint, and the {@code jwks_uri}.
 *
 * <p>The metadata document is the whole of discovery — a client with nothing but a 401 challenge
 * reads it to find out where to exchange a credential and which key set verifies the result — so what
 * it says is a contract, and a missing or wrong member strands every client. The token endpoint's
 * error behaviour matters for the same reason in reverse: RFC 6749 §5.2 error codes are what a client
 * branches on, and a body carrying a bearer credential must never be cacheable.
 */
class AuthorizationServerServletTest {

    private static final String ORIGIN = "https://halcyon.example";
    private static final String STORAGE = ORIGIN + "/W3Clws";
    private static final String ALICE = "https://alice.example/#me";
    private static final String CLIENT = "https://app.example/client";
    private static final String FORM = "application/x-www-form-urlencoded";

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private Path dir;
    private LwsStore store;
    private AccessTokenKeys keys;
    private AuthorizationServerSettings settings;
    private TokenExchange exchange;

    @BeforeEach
    void open() throws Exception {
        dir = Files.createTempDirectory("as-servlets");
        Constructor<LwsStore> ctor = LwsStore.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        store = ctor.newInstance(dir.resolve("tdb2").toString());
        keys = new AccessTokenKeys(store);
        settings = new AuthorizationServerSettings(ORIGIN, true, 300, true, List.of(STORAGE));
        CredentialVerifier suite = (token, req) ->
                new AgentContext(ALICE, CLIENT, "https://openid.example", List.of());
        exchange = new TokenExchange(settings, keys, List.of(suite), Clock.fixed(NOW,
                java.time.ZoneOffset.UTC));
    }

    @AfterEach
    void close() {
        try {
            store.raw().close();
        } catch (RuntimeException ignore) {
            // best effort
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignore) {
            // a leftover temp dir is harmless
        }
    }

    private FakeHttp.Response call(jakarta.servlet.http.HttpServlet servlet,
            jakarta.servlet.http.HttpServletRequest req) throws IOException {
        FakeHttp.Response out = new FakeHttp.Response();
        try {
            servlet.service(req, FakeHttp.response(out));
        } catch (jakarta.servlet.ServletException e) {
            throw new AssertionError(e);
        }
        return out;
    }

    private FakeHttp.Response token(Map<String, List<String>> params) throws IOException {
        return call(new TokenEndpointServlet(exchange),
                FakeHttp.request("POST", FORM, params));
    }

    private static Map<String, List<String>> validRequest() {
        String credential = Jwts.builder().subject(ALICE).issuer("https://openid.example")
                .claim("azp", CLIENT).issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(3600)))
                .signWith(Jwts.SIG.HS256.key().build()).compact();
        return new java.util.LinkedHashMap<>(Map.of(
                "grant_type", List.of(TokenExchange.GRANT_TYPE),
                "resource", List.of(STORAGE),
                "subject_token", List.of(credential),
                "subject_token_type", List.of(TokenExchange.TYPE_ID_TOKEN)));
    }

    // --- Metadata -----------------------------------------------------------

    @Test
    void theMetadataDocumentCarriesEverythingAClientNeedsToGetAToken() throws IOException {
        FakeHttp.Response out = call(
                new AuthorizationServerMetadataServlet(settings, exchange.subjectTokenTypes()),
                FakeHttp.get());

        assertEquals(200, out.status);
        assertEquals("application/json", out.contentType);
        JsonObject d = out.json();
        assertEquals(ORIGIN, d.getString("issuer"),
                "the issuer is the as_uri of every challenge and the iss of every token");
        assertEquals(ORIGIN + "/lws-as/token", d.getString("token_endpoint"));
        assertEquals(ORIGIN + "/lws-as/jwks", d.getString("jwks_uri"));
        assertEquals(List.of(TokenExchange.GRANT_TYPE), strings(d, "grant_types_supported"));
        assertEquals(List.of("none"), strings(d, "token_endpoint_auth_methods_supported"),
                "a client is identified by the URI in its credential, not by a registration here");
        assertTrue(strings(d, "claims_supported").containsAll(
                List.of("iss", "sub", "client_id", "aud", "exp", "iat", "jti")));
        assertEquals(List.of(TokenExchange.TYPE_ID_TOKEN, TokenExchange.TYPE_JWT),
                strings(d, "subject_token_types_supported"));
        assertEquals(List.of("https"), strings(d, "subject_identifier_types_supported"),
                "the subject identifiers the OpenID suite can verify are WebIDs over HTTPS; "
                        + "advertising did:web or did:key would claim a suite this module lacks");
    }

    @Test
    void theMetadataIsPubliclyCacheableBecauseItNamesNobody() throws IOException {
        FakeHttp.Response out = call(
                new AuthorizationServerMetadataServlet(settings, exchange.subjectTokenTypes()),
                FakeHttp.get());

        assertEquals("public, max-age=3600", out.headers.get("Cache-Control"));
    }

    @Test
    void aHeadOfTheMetadataSendsTheHeadersAndNoBody() throws IOException {
        FakeHttp.Response out = call(
                new AuthorizationServerMetadataServlet(settings, exchange.subjectTokenTypes()),
                FakeHttp.request("HEAD", null, Map.of()));

        assertEquals(200, out.status);
        assertTrue(out.contentLength > 0, "the length is still reported");
        assertEquals("", out.body());
    }

    @Test
    void theMetadataTakesOnlyReads() throws IOException {
        FakeHttp.Response out = call(
                new AuthorizationServerMetadataServlet(settings, exchange.subjectTokenTypes()),
                FakeHttp.request("POST", FORM, Map.of()));

        assertEquals(405, out.status);
        assertEquals("OPTIONS, HEAD, GET", out.headers.get("Allow"));
    }

    // --- JWKS ---------------------------------------------------------------

    @Test
    void theJwksServesTheVerificationKeyAsAJwkSet() throws IOException {
        FakeHttp.Response out = call(new JwksServlet(keys), FakeHttp.get());

        assertEquals(200, out.status);
        assertEquals("application/jwk-set+json", out.contentType);
        JsonObject jwk = out.json().getJsonArray("keys").get(0).asJsonObject();
        assertEquals(keys.keyId(), jwk.getString("kid"));
        assertEquals("ES256", jwk.getString("alg"));
        assertFalse(jwk.containsKey("d"), "the signing key never leaves the store");
    }

    // --- Token endpoint -----------------------------------------------------

    @Test
    void aValidRequestReturnsAnAccessTokenResponse() throws IOException {
        FakeHttp.Response out = token(validRequest());

        assertEquals(200, out.status);
        assertEquals("application/json", out.contentType);
        JsonObject d = out.json();
        assertEquals("Bearer", d.getString("token_type"));
        assertEquals(TokenExchange.TYPE_ACCESS_TOKEN, d.getString("issued_token_type"));
        assertEquals(300, d.getInt("expires_in"));
        assertTrue(d.getString("access_token").startsWith("ey"));
    }

    @Test
    void aTokenResponseIsNeverStored() throws IOException {
        // The body carries a bearer credential: a cache that kept it would hand it to the next
        // caller. RFC 6749 §5.1 requires no-store on every token response, success or error.
        assertEquals("no-store", token(validRequest()).headers.get("Cache-Control"));

        Map<String, List<String>> bad = validRequest();
        bad.put("resource", List.of("https://elsewhere.example/store"));
        assertEquals("no-store", token(bad).headers.get("Cache-Control"));
    }

    @Test
    void aRequestThatIsNotFormEncodedIsRefused() throws IOException {
        FakeHttp.Response out = call(new TokenEndpointServlet(exchange),
                FakeHttp.request("POST", "application/json", validRequest()));

        assertEquals(400, out.status);
        assertEquals("invalid_request", out.json().getString("error"));
    }

    @Test
    void aDuplicatedParameterIsRefused() throws IOException {
        // RFC 6749 §3.2: request parameters MUST NOT be included more than once. Accepting one
        // means picking a value, and which value is picked is the ambiguity used to smuggle a
        // second resource past a proxy that validated the first.
        Map<String, List<String>> params = validRequest();
        params.put("resource", List.of(STORAGE, "https://elsewhere.example/store"));

        FakeHttp.Response out = token(params);

        assertEquals(400, out.status);
        assertEquals("invalid_request", out.json().getString("error"));
    }

    @Test
    void anErrorResponseCarriesAnRfc6749ErrorCode() throws IOException {
        Map<String, List<String>> unknownStorage = validRequest();
        unknownStorage.put("resource", List.of("https://elsewhere.example/store"));
        FakeHttp.Response out = token(unknownStorage);
        assertEquals(400, out.status);
        assertEquals("invalid_target", out.json().getString("error"));
        assertTrue(out.json().containsKey("error_description"));

        Map<String, List<String>> wrongGrant = validRequest();
        wrongGrant.put("grant_type", List.of("client_credentials"));
        assertEquals("unsupported_grant_type", token(wrongGrant).json().getString("error"));
    }

    @Test
    void aTokenRequestIsAPost() throws IOException {
        FakeHttp.Response out = call(new TokenEndpointServlet(exchange),
                FakeHttp.request("GET", null, Map.of()));

        assertEquals(405, out.status);
        assertEquals("OPTIONS, POST", out.headers.get("Allow"));
        assertEquals("invalid_request", out.json().getString("error"));
    }

    @Test
    void optionsAdvertisesTheMethodsWithoutABody() throws IOException {
        FakeHttp.Response token = call(new TokenEndpointServlet(exchange),
                FakeHttp.request("OPTIONS", null, Map.of()));
        assertEquals(204, token.status);
        assertEquals("OPTIONS, POST", token.headers.get("Allow"));

        FakeHttp.Response jwks = call(new JwksServlet(keys),
                FakeHttp.request("OPTIONS", null, Map.of()));
        assertEquals(204, jwks.status);
        assertEquals("OPTIONS, HEAD, GET", jwks.headers.get("Allow"));
    }

    private static List<String> strings(JsonObject doc, String key) {
        return doc.getJsonArray(key).stream().map(v -> ((JsonString) v).getString()).toList();
    }
}
