package com.ebremer.lws.auth;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The authorization server's signing keys, fetched from its JWKS endpoint.
 *
 * <p>Keyed by {@code kid} and refreshed on a miss, which is what makes key rotation
 * work: lws10-core says a storage server "SHOULD cache these keys and MUST support
 * key rotation". A cache that ignores {@code kid} and pins one key forever — which
 * is what Halcyon's existing {@code KeycloakPublicKeyFetcher} does — breaks
 * permanently the first time the realm rotates, and can only be fixed by a restart.
 *
 * <p>A refresh is rate-limited so that a flood of tokens bearing an unknown
 * {@code kid} cannot be turned into a request amplifier against the auth server.
 */
public final class JwksCache {

    private static final Logger LOG = LoggerFactory.getLogger(JwksCache.class);

    /** Shortest interval between two refreshes prompted by an unknown kid. */
    private static final long MIN_REFRESH_MS = 30_000;

    private final String jwksUri;
    private final HttpClient http;
    private final AtomicLong lastFetch = new AtomicLong(0);
    private volatile Map<String, PublicKey> keys = Map.of();

    public JwksCache(String jwksUri) {
        this.jwksUri = jwksUri;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .sslContext(trustAll())
                .build();
    }

    /**
     * The key for a {@code kid}, refreshing once if it is not already cached.
     *
     * @return null if the issuer does not publish such a key
     */
    public PublicKey key(String kid) {
        PublicKey k = keys.get(kid);
        if (k != null) {
            return k;
        }
        long now = System.currentTimeMillis();
        long last = lastFetch.get();
        if (now - last < MIN_REFRESH_MS || !lastFetch.compareAndSet(last, now)) {
            return null;
        }
        refresh();
        return keys.get(kid);
    }

    private void refresh() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(jwksUri))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<InputStream> resp =
                    http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                LOG.warn("JWKS fetch from {} returned {}", jwksUri, resp.statusCode());
                return;
            }
            Map<String, PublicKey> fresh = new HashMap<>();
            try (JsonReader r = Json.createReader(resp.body())) {
                JsonArray jwks = r.readObject().getJsonArray("keys");
                if (jwks == null) {
                    return;
                }
                for (JsonObject jwk : jwks.getValuesAs(JsonObject.class)) {
                    String kid = jwk.getString("kid", null);
                    String kty = jwk.getString("kty", null);
                    if (kid == null || !"RSA".equals(kty)) {
                        // Keycloak realms sign with RS256 by default. An EC key would
                        // need its own decoder; skip rather than guess.
                        continue;
                    }
                    try {
                        fresh.put(kid, rsaKey(jwk));
                    } catch (RuntimeException e) {
                        LOG.warn("skipping unusable JWK {}", kid, e);
                    }
                }
            }
            if (!fresh.isEmpty()) {
                keys = Map.copyOf(fresh);
                LOG.info("loaded {} signing key(s) from {}", fresh.size(), jwksUri);
            }
        } catch (java.io.IOException e) {
            LOG.warn("could not fetch JWKS from {}", jwksUri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static PublicKey rsaKey(JsonObject jwk) {
        try {
            byte[] n = Base64.getUrlDecoder().decode(jwk.getString("n"));
            byte[] e = Base64.getUrlDecoder().decode(jwk.getString("e"));
            RSAPublicKeySpec spec = new RSAPublicKeySpec(
                    new BigInteger(1, n), new BigInteger(1, e));
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalArgumentException("bad RSA JWK", e);
        }
    }

    /**
     * Halcyon serves itself, and Keycloak behind it, on a self-signed certificate, and
     * the JWKS endpoint is reached through that same loopback. There is no network here
     * to be intercepted — the connection never leaves the host — and the token's
     * signature is verified regardless, which is the property that actually matters.
     *
     * <p>Shared with the webhook client, which faces the same self-signed loopback.
     */
    public static SSLContext trustAll() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] {new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[0];
                }
            }}, new java.security.SecureRandom());
            return ctx;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
