package com.ebremer.lws.auth.oidc;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.auth.InvalidBearerTokenException;
import com.ebremer.lws.auth.PresentedToken;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link LwsOidcVerifier} end to end: a real RS256 ID Token whose CID (served from a loopback
 * server) names the token's issuer as its OpenID Provider verifies to an AgentContext carrying
 * the WebID; a token with a non-URL {@code sub} is not this verifier's kind; a CID that names a
 * different issuer, and an expired token, are rejected.
 */
class LwsOidcVerifierTest {

    private static final String KID = "k1";
    private static final KeyPair RSA = genRsa();

    private final LwsOidcVerifier verifier =
            new LwsOidcVerifier(new LwsOidcSettings(true, Set.of("127.0.0.1")));
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void verifiesAnLwsCredentialToItsWebId() throws IOException {
        String base = start(null); // CID names `base` (== iss) as its OpenID Provider
        String sub = base + "/cid";
        String token = mint(base, sub, Date.from(Instant.now().plusSeconds(300)));

        AgentContext ctx = verifier.tryAuthenticate(PresentedToken.parse("Bearer " + token), null);

        assertEquals(sub, ctx.webId(), "the WebID sub becomes the authenticated identity");
        assertEquals(base, ctx.issuer());
        assertEquals("client-x", ctx.clientId());
    }

    @Test
    void aNonUrlSubIsNotThisVerifiersKind() {
        String token = mint("https://issuer.example", "urn:uuid:not-a-webid",
                Date.from(Instant.now().plusSeconds(300)));
        assertNull(verifier.tryAuthenticate(PresentedToken.parse("Bearer " + token), null),
                "an opaque sub must fall through to the next verifier, not be rejected");
    }

    @Test
    void rejectsWhenTheCidNamesADifferentIssuer() throws IOException {
        String base = start("https://someone-else.example"); // CID points elsewhere
        String token = mint(base, base + "/cid", Date.from(Instant.now().plusSeconds(300)));
        assertThrows(InvalidBearerTokenException.class,
                () -> verifier.tryAuthenticate(PresentedToken.parse("Bearer " + token), null));
    }

    @Test
    void rejectsAnExpiredCredential() throws IOException {
        String base = start(null);
        String token = mint(base, base + "/cid", Date.from(Instant.now().minusSeconds(3600)));
        assertThrows(InvalidBearerTokenException.class,
                () -> verifier.tryAuthenticate(PresentedToken.parse("Bearer " + token), null));
    }

    // --- fixtures -------------------------------------------------------------------------

    private String mint(String iss, String sub, Date exp) {
        return Jwts.builder()
                .header().keyId(KID).and()
                .issuer(iss).subject(sub).claim("azp", "client-x")
                .expiration(exp)
                .signWith(RSA.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    /** Serve /cid, discovery and /jwks. The CID's OpenIdProvider endpoint is {@code base} unless overridden. */
    private String start(String cidEndpointOverride) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        String cidEndpoint = cidEndpointOverride != null ? cidEndpointOverride : base;
        String cid = "<" + base + "/cid> <https://www.w3.org/ns/did#service> [ "
                + "a <https://www.w3.org/ns/lws#OpenIdProvider> ; "
                + "<https://www.w3.org/ns/did#serviceEndpoint> <" + cidEndpoint + "> ] .";
        String discovery = "{\"issuer\":\"" + base + "\",\"jwks_uri\":\"" + base + "/jwks\"}";
        String jwks = rsaJwks(KID, (RSAPublicKey) RSA.getPublic());
        server.createContext("/cid", ex -> respond(ex, cid, "text/turtle"));
        server.createContext("/.well-known/openid-configuration", ex -> respond(ex, discovery, "application/json"));
        server.createContext("/jwks", ex -> respond(ex, jwks, "application/json"));
        server.start();
        return base;
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
