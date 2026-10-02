package com.ebremer.lws.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.auth.AccessTokenValidator;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.auth.CredentialVerifier;
import com.ebremer.lws.auth.InvalidBearerTokenException;
import com.ebremer.lws.auth.PresentedToken;
import com.ebremer.lws.store.LwsStore;
import io.jsonwebtoken.Jwts;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins lws10-core §Authorization end to end: a credential is exchanged for an RFC 9068 access
 * token, and a storage validates that token as the spec tells it to.
 *
 * <p>This is the part of the protocol this module had no implementation of at all — it accepted an
 * authentication credential directly at a storage, which works but gives the credential the
 * storage's authority. The property the exchange buys, and the one most of these tests are about,
 * is confinement: a token names exactly one storage in {@code aud} and lives 300 seconds, so it is
 * worth nothing at another storage and little after a few minutes. Every check below is a way that
 * confinement could be lost.
 *
 * <p>The authentication suite is stubbed. Which credentials are valid is
 * {@code LwsOidcVerifierTest}'s subject; what is under test here is what the authorization server
 * does with a credential once a suite has accepted or refused it.
 */
class TokenExchangeTest {

    private static final String ORIGIN = "https://halcyon.example";
    private static final String STORAGE = ORIGIN + "/W3Clws";
    private static final String OTHER_STORAGE = ORIGIN + "/W3ClwsSlash";
    private static final String UNKNOWN_STORAGE = "https://elsewhere.example/store";
    private static final String ALICE = "https://alice.example/#me";
    private static final String CLIENT = "https://app.example/client";

    /**
     * The exchange runs on a fixed clock so a token's lifetime can be asserted exactly, but the
     * validator runs on the system clock — that is the real split between the two roles. So "now"
     * has to be the real now, truncated to whole seconds because a JWT's exp and iat are.
     */
    private static final Instant NOW = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

    private Path dir;
    private LwsStore store;
    private AccessTokenKeys keys;

    @BeforeEach
    void open() throws Exception {
        dir = Files.createTempDirectory("token-exchange");
        Constructor<LwsStore> ctor = LwsStore.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        store = ctor.newInstance(dir.resolve("tdb2").toString());
        keys = new AccessTokenKeys(store);
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

    // --- Fixtures -----------------------------------------------------------

    private static AuthorizationServerSettings settings() {
        return new AuthorizationServerSettings(ORIGIN, true, 300, true,
                List.of(STORAGE, OTHER_STORAGE));
    }

    /** A suite that accepts anything and reports Alice. */
    private static CredentialVerifier accepts(String webId, String clientId) {
        return (token, req) -> new AgentContext(webId, clientId, "https://openid.example",
                List.of());
    }

    /** A suite that recognizes the credential and refuses it. */
    private static CredentialVerifier refuses() {
        return (token, req) -> {
            throw new InvalidBearerTokenException("invalid_token", "nope");
        };
    }

    private TokenExchange exchange(CredentialVerifier... suites) {
        return new TokenExchange(settings(), keys, List.of(suites),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** A credential-shaped JWT. Its signature is never checked here — the suite is stubbed. */
    private static String credential(Instant expires) {
        var b = Jwts.builder().subject(ALICE).issuer("https://openid.example")
                .claim("azp", CLIENT).issuedAt(Date.from(NOW));
        if (expires != null) {
            b.expiration(Date.from(expires));
        }
        return b.signWith(Jwts.SIG.HS256.key().build()).compact();
    }

    private static Map<String, String> request(String resource, String subjectToken) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("grant_type", TokenExchange.GRANT_TYPE);
        p.put("resource", resource);
        p.put("subject_token", subjectToken);
        p.put("subject_token_type", TokenExchange.TYPE_ID_TOKEN);
        return p;
    }

    private static TokenExchange.OAuthError refused(Runnable r) {
        return assertThrows(TokenExchange.OAuthError.class, r::run);
    }

    // --- Issuing ------------------------------------------------------------

    @Test
    void anExchangedTokenCarriesEveryClaimTheSpecRequires() {
        TokenExchange.Issued issued = exchange(accepts(ALICE, CLIENT))
                .exchange(request(STORAGE, credential(NOW.plusSeconds(3600))), null);

        var claims = Jwts.parser().verifyWith(keys.verificationKey()).build()
                .parseSignedClaims(issued.accessToken());

        assertEquals(AccessTokenKeys.AT_JWT, claims.getHeader().getType(),
                "RFC 9068: an access token's typ is at+jwt, which is what keeps it from being "
                        + "confused with an ID token");
        assertEquals(keys.keyId(), claims.getHeader().getKeyId());
        assertEquals("ES256", claims.getHeader().getAlgorithm());

        var c = claims.getPayload();
        assertEquals(ORIGIN, c.getIssuer());
        assertEquals(ALICE, c.getSubject());
        assertEquals(CLIENT, c.get("client_id", String.class));
        assertEquals(java.util.Set.of(STORAGE), c.getAudience());
        assertNotNull(c.getId(), "jti is REQUIRED");
        assertEquals(Date.from(NOW), c.getIssuedAt());
        assertEquals(Date.from(NOW.plusSeconds(300)), c.getExpiration());
        assertEquals(300, issued.expiresIn());
    }

    @Test
    void aTokenNeverOutlivesTheCredentialItWasExchangedFor() {
        // A credential with 30 seconds left cannot be traded for 300: that would let an expiring
        // credential outlive its own expiry by being spent at the last moment.
        TokenExchange.Issued issued = exchange(accepts(ALICE, CLIENT))
                .exchange(request(STORAGE, credential(NOW.plusSeconds(30))), null);

        assertEquals(30, issued.expiresIn());
    }

    @Test
    void anExpiredCredentialBuysNothing() {
        assertEquals("invalid_request", refused(() -> exchange(accepts(ALICE, CLIENT))
                .exchange(request(STORAGE, credential(NOW.minusSeconds(1))), null)).error());
    }

    @Test
    void aCredentialWithNoExpiryGetsTheConfiguredLifetime() {
        assertEquals(300, exchange(accepts(ALICE, CLIENT))
                .exchange(request(STORAGE, credential(null)), null).expiresIn());
    }

    @Test
    void eachStorageGetsItsOwnAudience() {
        var ex = exchange(accepts(ALICE, CLIENT));
        String cred = credential(NOW.plusSeconds(3600));

        assertEquals(java.util.Set.of(OTHER_STORAGE), audienceOf(
                ex.exchange(request(OTHER_STORAGE, cred), null).accessToken()));
        assertEquals(java.util.Set.of(STORAGE), audienceOf(
                ex.exchange(request(STORAGE + "/", cred), null).accessToken()),
                "the storage URI's trailing slash names the same storage");
    }

    // --- Refusals -----------------------------------------------------------

    @Test
    void aResourceThatIsNotAStorageOfThisInstanceIsAnInvalidTarget() {
        // "The authorization server MUST reject any request in which the resource parameter
        // identifies an unknown or untrusted storage." Without this the endpoint would mint
        // tokens for anyone's storage, and a storage trusting this issuer would honour them.
        var ex = exchange(accepts(ALICE, CLIENT));
        String cred = credential(NOW.plusSeconds(3600));

        assertEquals("invalid_target",
                refused(() -> ex.exchange(request(UNKNOWN_STORAGE, cred), null)).error());
        assertEquals("invalid_target",
                refused(() -> ex.exchange(request(STORAGE + "/sub/resource", cred), null)).error(),
                "a resource inside a storage is not the storage");
    }

    @Test
    void resourceIsRequired() {
        var ex = exchange(accepts(ALICE, CLIENT));
        Map<String, String> p = request(STORAGE, credential(null));
        p.remove("resource");

        assertEquals("invalid_request", refused(() -> ex.exchange(p, null)).error());
    }

    @Test
    void onlyTheTokenExchangeGrantIsSupported() {
        var ex = exchange(accepts(ALICE, CLIENT));
        Map<String, String> p = request(STORAGE, credential(null));
        p.put("grant_type", "authorization_code");

        assertEquals("unsupported_grant_type", refused(() -> ex.exchange(p, null)).error());
    }

    @Test
    void anAudienceThatDisagreesWithResourceIsRefused() {
        var ex = exchange(accepts(ALICE, CLIENT));
        Map<String, String> p = request(STORAGE, credential(null));
        p.put("audience", OTHER_STORAGE);

        assertEquals("invalid_target", refused(() -> ex.exchange(p, null)).error());
    }

    @Test
    void delegationIsRefusedRatherThanIgnored() {
        // Ignoring actor_token would issue a token for the subject while the client asked to act
        // on someone else's behalf -- a silently different grant from the one requested.
        var ex = exchange(accepts(ALICE, CLIENT));
        Map<String, String> p = request(STORAGE, credential(null));
        p.put("actor_token", credential(null));

        assertEquals("invalid_request", refused(() -> ex.exchange(p, null)).error());
    }

    @Test
    void onlyAnAccessTokenCanBeRequested() {
        var ex = exchange(accepts(ALICE, CLIENT));
        Map<String, String> p = request(STORAGE, credential(null));
        p.put("requested_token_type", TokenExchange.TYPE_ID_TOKEN);

        assertEquals("invalid_request", refused(() -> ex.exchange(p, null)).error());
    }

    @Test
    void anUnsupportedSubjectTokenTypeIsRefused() {
        var ex = exchange(accepts(ALICE, CLIENT));
        Map<String, String> p = request(STORAGE, credential(null));
        p.put("subject_token_type", "urn:ietf:params:oauth:token-type:saml2");

        assertEquals("invalid_request", refused(() -> ex.exchange(p, null)).error());
    }

    @Test
    void theGenericJwtTypeIsStillAcceptedThoughNoLongerAdvertised() {
        // The metadata names only ...:id_token now (...:jwt is the SSI-CID suite's type, which this
        // module does not implement), but a client written against the earlier metadata that sends
        // the same ID Token as ...:jwt still gets its access token.
        var ex = exchange(accepts(ALICE, CLIENT));
        Map<String, String> p = request(STORAGE, credential(NOW.plusSeconds(3600)));
        p.put("subject_token_type", TokenExchange.TYPE_JWT);

        assertNotNull(ex.exchange(p, null).accessToken());
        assertEquals(List.of(TokenExchange.TYPE_ID_TOKEN), ex.subjectTokenTypes());
    }

    @Test
    void aCredentialNoSuiteAcceptsBuysNothing() {
        String cred = credential(NOW.plusSeconds(3600));

        assertEquals("invalid_request", refused(() ->
                exchange(refuses()).exchange(request(STORAGE, cred), null)).error());
        assertEquals("invalid_request", refused(() ->
                exchange().exchange(request(STORAGE, cred), null)).error(),
                "with no suite configured nothing can be exchanged");
    }

    @Test
    void aRefusingSuiteStopsTheChain() {
        // A tampered credential must not be laundered past its own verifier by a later suite.
        assertEquals("invalid_request", refused(() -> exchange(refuses(), accepts(ALICE, CLIENT))
                .exchange(request(STORAGE, credential(null)), null)).error());
    }

    @Test
    void aCredentialWithNoClientIdentifierBuysNothing() {
        // lws10-core makes client_id REQUIRED in an access token and lws10-authn-openid puts it in
        // azp. Inventing one would be a lie about who asked, and an acp:client matcher would then
        // be evaluated against a fiction.
        assertEquals("invalid_request", refused(() -> exchange(accepts(ALICE, null))
                .exchange(request(STORAGE, credential(null)), null)).error());
        assertEquals("invalid_request", refused(() -> exchange(accepts(ALICE, "not-a-uri"))
                .exchange(request(STORAGE, credential(null)), null)).error());
    }

    @Test
    void aCredentialWhoseSubjectIsNotAUriBuysNothing() {
        assertEquals("invalid_request", refused(() -> exchange(accepts("alice", CLIENT))
                .exchange(request(STORAGE, credential(null)), null)).error());
    }

    // --- Validation by a storage --------------------------------------------

    @Test
    void aStorageAcceptsATokenMintedForIt() {
        String token = exchange(accepts(ALICE, CLIENT))
                .exchange(request(STORAGE, credential(NOW.plusSeconds(3600))), null).accessToken();

        AgentContext agent = validator(STORAGE).tryAuthenticate(presented(token), null);

        assertNotNull(agent);
        assertEquals(ALICE, agent.webId());
        assertEquals(CLIENT, agent.clientId());
        assertEquals(ORIGIN, agent.issuer());
    }

    @Test
    void aStorageRefusesATokenMintedForAnother() {
        // The confinement property: one storage's token is worthless at the next.
        String token = exchange(accepts(ALICE, CLIENT))
                .exchange(request(OTHER_STORAGE, credential(NOW.plusSeconds(3600))), null)
                .accessToken();

        var e = assertThrows(InvalidBearerTokenException.class,
                () -> validator(STORAGE).tryAuthenticate(presented(token), null));
        assertEquals("invalid_token", e.error());
    }

    @Test
    void aStorageRefusesATokenWithSeveralAudiences() {
        // "Verify the aud claim contains exactly one value": a token good for several storages is
        // one that spreads a single storage's compromise.
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ORIGIN);
        claims.put("sub", ALICE);
        claims.put("client_id", CLIENT);
        claims.put("aud", List.of(STORAGE, OTHER_STORAGE));
        claims.put("jti", "x");
        String token = keys.sign(claims, Date.from(NOW), Date.from(NOW.plusSeconds(300)));

        assertEquals("invalid_token", assertThrows(InvalidBearerTokenException.class,
                () -> validator(STORAGE).tryAuthenticate(presented(token), null)).error());
    }

    @Test
    void aStorageRefusesAnExpiredToken() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ORIGIN);
        claims.put("sub", ALICE);
        claims.put("client_id", CLIENT);
        claims.put("aud", STORAGE);
        claims.put("jti", "x");
        String token = keys.sign(claims, Date.from(NOW.minusSeconds(4000)),
                Date.from(NOW.minusSeconds(3600)));

        assertEquals("invalid_token", assertThrows(InvalidBearerTokenException.class,
                () -> validator(STORAGE).tryAuthenticate(presented(token), null)).error());
    }

    @Test
    void aStorageRefusesATokenMissingAClaimItMustAuthorizeAgainst() {
        for (String missing : List.of("client_id", "jti", "sub")) {
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", ORIGIN);
            claims.put("sub", ALICE);
            claims.put("client_id", CLIENT);
            claims.put("aud", STORAGE);
            claims.put("jti", "x");
            claims.remove(missing);
            String token = keys.sign(claims, Date.from(NOW), Date.from(NOW.plusSeconds(300)));

            assertEquals("invalid_token", assertThrows(InvalidBearerTokenException.class,
                    () -> validator(STORAGE).tryAuthenticate(presented(token), null),
                    "a token with no " + missing + " must be refused").error());
        }
    }

    @Test
    void aStorageRefusesATokenSignedWithASymmetricKey() {
        // Algorithm confusion: the storage holds a verification key, so a token claiming HMAC is
        // refused outright rather than handed to a parser along with that key.
        String hmac = Jwts.builder()
                .header().type(AccessTokenKeys.AT_JWT).and()
                .issuer(ORIGIN).subject(ALICE).claim("client_id", CLIENT)
                .audience().add(STORAGE).and()
                .id("x").issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(300)))
                .signWith(Jwts.SIG.HS256.key().build())
                .compact();

        assertEquals("invalid_token", assertThrows(InvalidBearerTokenException.class,
                () -> validator(STORAGE).tryAuthenticate(presented(hmac), null)).error());
    }

    @Test
    void anAuthenticationCredentialIsNotAnAccessToken() {
        // A credential from some other issuer is not this validator's kind: it returns null so a
        // suite can try it, rather than rejecting it as a bad access token.
        assertNull(validator(STORAGE).tryAuthenticate(presented(credential(null)), null));
    }

    @Test
    void aTokenFromAnotherIssuerIsRefused() {
        // Claims our issuer's shape (at+jwt) but was signed by a key we do not hold.
        String foreign = Jwts.builder()
                .header().type(AccessTokenKeys.AT_JWT).and()
                .issuer(ORIGIN).subject(ALICE).claim("client_id", CLIENT)
                .audience().add(STORAGE).and()
                .id("x").issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(300)))
                .signWith(Jwts.SIG.ES256.keyPair().build().getPrivate())
                .compact();

        assertEquals("invalid_token", assertThrows(InvalidBearerTokenException.class,
                () -> validator(STORAGE).tryAuthenticate(presented(foreign), null)).error());
    }

    // --- Metadata -----------------------------------------------------------

    @Test
    void theMetadataDocumentIsWhereRfc8414SaysAndSaysWhatLwsAdds() {
        assertEquals("/.well-known/lws-configuration",
                AuthorizationServerSettings.METADATA_PATH);
        assertEquals(ORIGIN + "/.well-known/lws-configuration", settings().metadataUri());
        assertEquals(ORIGIN + "/lws-as/token", settings().tokenEndpointUri());
        assertEquals(ORIGIN + "/lws-as/jwks", settings().jwksUri());
        assertTrue(exchange(accepts(ALICE, CLIENT)).subjectTokenTypes()
                .contains(TokenExchange.TYPE_ID_TOKEN));
        assertTrue(exchange().subjectTokenTypes().isEmpty(),
                "with no suite the server advertises no subject token type rather than a "
                        + "capability it does not have");
    }

    @Test
    void theJwkSetPublishesTheVerificationKeyAndNothingElse() {
        var jwk = keys.jwkSet().getJsonArray("keys").get(0).asJsonObject();

        assertEquals(keys.keyId(), jwk.getString("kid"));
        assertEquals("EC", jwk.getString("kty"));
        assertEquals("P-256", jwk.getString("crv"));
        assertEquals("ES256", jwk.getString("alg"));
        assertTrue(!jwk.containsKey("d"), "the signing key never leaves the store");
    }

    @Test
    void theSigningKeyIsStableAcrossRestarts() {
        // A key minted per start would invalidate every outstanding token and change the published
        // jwks on every bounce.
        assertEquals(keys.keyId(), new AccessTokenKeys(store).keyId());
    }

    // --- Helpers ------------------------------------------------------------

    private AccessTokenValidator validator(String realm) {
        return new AccessTokenValidator(settings(), keys, realm);
    }

    private static PresentedToken presented(String token) {
        return PresentedToken.parse("Bearer " + token);
    }

    private java.util.Set<String> audienceOf(String token) {
        return Jwts.parser().verifyWith(keys.verificationKey()).build()
                .parseSignedClaims(token).getPayload().getAudience();
    }
}
