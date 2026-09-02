package com.ebremer.lws.auth.oidc;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link OidcKeys}: discovers a loopback OP, confirms it self-identifies, fetches its JWKS and
 * returns the RSA/EC signing key for a kid — and refuses on issuer mismatch, unknown kid,
 * alg/key mismatch, or a non-allow-listed internal host.
 */
class OidcKeysTest {

    private static final Set<String> LOOPBACK = Set.of("127.0.0.1");
    private static final KeyPair RSA = gen("RSA", 2048, null);
    private static final KeyPair EC = gen("EC", 0, "secp256r1");

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void resolvesAnRsaSigningKey() throws IOException {
        String iss = start(null, rsaJwks("rsa-1", (RSAPublicKey) RSA.getPublic()));
        PublicKey key = new OidcKeys().signingKey(iss, "rsa-1", "RS256", LOOPBACK);
        assertEquals(((RSAPublicKey) RSA.getPublic()).getModulus(), ((RSAPublicKey) key).getModulus());
    }

    @Test
    void resolvesAnEcSigningKey() throws IOException {
        String iss = start(null, ecJwks("ec-1", (ECPublicKey) EC.getPublic()));
        PublicKey key = new OidcKeys().signingKey(iss, "ec-1", "ES256", LOOPBACK);
        assertEquals(((ECPublicKey) EC.getPublic()).getW(), ((ECPublicKey) key).getW());
    }

    @Test
    void refusesWhenDiscoveryIssuerDoesNotMatch() throws IOException {
        String iss = start("https://evil.example", rsaJwks("rsa-1", (RSAPublicKey) RSA.getPublic()));
        assertThrows(OidcKeys.OidcException.class,
                () -> new OidcKeys().signingKey(iss, "rsa-1", "RS256", LOOPBACK));
    }

    @Test
    void refusesAnUnknownKid() throws IOException {
        String iss = start(null, rsaJwks("rsa-1", (RSAPublicKey) RSA.getPublic()));
        assertThrows(OidcKeys.OidcException.class,
                () -> new OidcKeys().signingKey(iss, "not-published", "RS256", LOOPBACK));
    }

    @Test
    void refusesWhenTheAlgorithmDoesNotMatchTheKey() throws IOException {
        String iss = start(null, rsaJwks("rsa-1", (RSAPublicKey) RSA.getPublic()));
        assertThrows(OidcKeys.OidcException.class,
                () -> new OidcKeys().signingKey(iss, "rsa-1", "ES256", LOOPBACK));
    }

    @Test
    void refusesANonAllowListedInternalHost() throws IOException {
        String iss = start(null, rsaJwks("rsa-1", (RSAPublicKey) RSA.getPublic()));
        assertThrows(SsrfGuard.BlockedException.class,
                () -> new OidcKeys().signingKey(iss, "rsa-1", "RS256", Set.of()));
    }

    // --- fixtures -------------------------------------------------------------------------

    /** Serve discovery (issuer optionally forced to {@code issuerInDiscovery}) + a JWKS. */
    private String start(String issuerInDiscovery, String jwksBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        String iss = issuerInDiscovery != null ? issuerInDiscovery : base;
        String discovery = "{\"issuer\":\"" + iss + "\",\"jwks_uri\":\"" + base + "/jwks\"}";
        server.createContext("/.well-known/openid-configuration", ex -> respond(ex, discovery));
        server.createContext("/jwks", ex -> respond(ex, jwksBody));
        server.start();
        return base;
    }

    private static void respond(HttpExchange ex, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private static String rsaJwks(String kid, RSAPublicKey k) {
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + kid + "\",\"n\":\""
                + uint(k.getModulus()) + "\",\"e\":\"" + uint(k.getPublicExponent()) + "\"}]}";
    }

    private static String ecJwks(String kid, ECPublicKey k) {
        return "{\"keys\":[{\"kty\":\"EC\",\"kid\":\"" + kid + "\",\"crv\":\"P-256\",\"x\":\""
                + fixed(k.getW().getAffineX(), 32) + "\",\"y\":\"" + fixed(k.getW().getAffineY(), 32) + "\"}]}";
    }

    /** Minimal unsigned big-endian, base64url — the JWK integer encoding. */
    private static String uint(BigInteger bi) {
        byte[] b = bi.toByteArray();
        int i = 0;
        while (i < b.length - 1 && b[i] == 0) {
            i++;
        }
        return b64(Arrays.copyOfRange(b, i, b.length));
    }

    /** Fixed-length unsigned big-endian (EC coordinates), base64url. */
    private static String fixed(BigInteger bi, int len) {
        byte[] src = bi.toByteArray();
        byte[] out = new byte[len];
        int copy = Math.min(src.length, len);
        System.arraycopy(src, src.length - copy, out, len - copy, copy);
        return b64(out);
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static KeyPair gen(String algo, int rsaBits, String curve) {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance(algo);
            if (curve != null) {
                g.initialize(new ECGenParameterSpec(curve));
            } else {
                g.initialize(rsaBits);
            }
            return g.generateKeyPair();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
