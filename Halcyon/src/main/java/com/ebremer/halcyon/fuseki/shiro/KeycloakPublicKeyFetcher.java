package com.ebremer.halcyon.fuseki.shiro;

import com.ebremer.halcyon.server.SslConfig;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caches the realm's JWKS signing keys, indexed by {@code kid} (M4).
 * <p>
 * Previously this fetched the JWKS once, kept a SINGLE key chosen by "last RS256
 * in the array wins", and never refreshed — so a Keycloak key <em>rotation</em>
 * (the realm publishes the old and new keys together, and tokens carry the
 * signing key's {@code kid}) permanently broke every JWT until restart, as did a
 * single transient fetch failure at startup. Now:
 * <ul>
 *   <li>every usable RSA <em>signature</em> key is cached, keyed by its {@code kid};</li>
 *   <li>an unknown {@code kid} triggers a re-fetch (throttled), so a rotation
 *       heals itself without a restart;</li>
 *   <li>a failed fetch keeps any previously-good keys and is retried later,
 *       instead of poisoning the singleton with a null key forever;</li>
 *   <li>init is properly synchronized (it was an unguarded lazy singleton).</li>
 * </ul>
 *
 * @author erich
 */
public class KeycloakPublicKeyFetcher {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakPublicKeyFetcher.class);

    /**
     * Floor between JWKS fetches. Without it, a caller presenting tokens with
     * random/unknown kids would drive one Keycloak round-trip per request.
     */
    private static final long MIN_REFETCH_INTERVAL_MS = 10_000L;
    /** Shorter floor while we hold no usable keys/issuer — see {@link #refresh()}. */
    private static final long MIN_RETRY_WHEN_UNHEALTHY_MS = 1_000L;
    private static final int HTTP_TIMEOUT_MS = 10_000;

    private static volatile KeycloakPublicKeyFetcher kpkf = null;

    private final String jwksUrl;
    private final String discoveryUrl;
    private final Object fetchLock = new Object();
    private volatile Map<String, PublicKey> keysByKid = Map.of();
    private volatile String issuer = null;
    private long lastFetchAttempt = 0L;   // guarded by fetchLock

    public KeycloakPublicKeyFetcher() {
        String realmBase = HalcyonSettings.getSettings().getProxyHostName()
                + "/auth/realms/" + HalcyonSettings.REALM;
        jwksUrl = realmBase + "/protocol/openid-connect/certs";
        discoveryUrl = realmBase + "/.well-known/openid-configuration";
    }

    /**
     * The realm's authoritative {@code issuer} (H2), as the realm itself
     * advertises it in its OIDC discovery document — never guessed from local
     * settings. That matters: this codebase reaches Keycloak through three
     * different bases ({@code HostName} / {@code ProxyHostName} /
     * {@code AuthServer}) and Keycloak stamps {@code iss} from its own
     * configured frontend URL, so any locally-constructed expectation would be
     * a coin flip that rejects every token when it loses.
     *
     * @return the issuer, or {@code null} if it could not be resolved — callers
     *         must then reject the token (fail closed).
     */
    public String getIssuer() {
        String local = issuer;
        if (local == null) {
            refresh();
            local = issuer;
        }
        return local;
    }

    /** Double-checked, properly synchronized singleton (init used to race). */
    public static KeycloakPublicKeyFetcher getKeycloakPublicKeyFetcher() {
        KeycloakPublicKeyFetcher local = kpkf;
        if (local == null) {
            synchronized (KeycloakPublicKeyFetcher.class) {
                local = kpkf;
                if (local == null) {
                    local = new KeycloakPublicKeyFetcher();
                    kpkf = local;
                    // Best effort: a failure here is recoverable — resolve()
                    // retries once past the throttle rather than failing forever.
                    local.refresh();
                }
            }
        }
        return local;
    }

    /**
     * The signing key a JWS header's {@code kid} names, or {@code null} if the
     * realm does not publish it (caller must then reject the token — fail closed).
     * An unknown kid re-fetches the JWKS once, throttled, so a key rotation is
     * picked up automatically.
     */
    public PublicKey resolve(String kid) {
        PublicKey key = lookup(kid);
        if (key != null) {
            return key;
        }
        // Unknown kid (rotation) or empty cache (startup fetch failed) — re-fetch.
        if (refresh()) {
            key = lookup(kid);
            if (key != null) {
                return key;
            }
        }
        logger.warn("No Keycloak JWKS signing key for kid={} (known: {})", kid, keysByKid.keySet());
        return null;
    }

    private PublicKey lookup(String kid) {
        Map<String, PublicKey> keys = keysByKid;
        if (kid != null) {
            return keys.get(kid);
        }
        // No kid in the header: only safe when the realm publishes exactly one
        // signing key, otherwise we cannot know which one to trust.
        return (keys.size() == 1) ? keys.values().iterator().next() : null;
    }

    /**
     * Re-fetch the JWKS + issuer, throttled. Existing data is retained if the
     * fetch fails.
     * <p>
     * The throttle is deliberately asymmetric. Once we HAVE usable data, back off
     * hard: otherwise a caller presenting tokens with random unknown kids drives
     * one Keycloak round-trip per request. While we have NOTHING, back off only
     * briefly — auth is failing anyway, so healing fast matters more, and this
     * really happens: discovery resolves through this application's own /auth
     * proxy, so the very first attempt (at startup, before the connector is
     * listening) fails with ConnectException. A flat 10 s window there would have
     * made the first sign-in within 10 s of boot fail for no good reason. The
     * short floor still bounds the load if Keycloak is genuinely down.
     *
     * @return true if the cache now holds at least one key.
     */
    private boolean refresh() {
        synchronized (fetchLock) {
            long now = System.currentTimeMillis();
            boolean healthy = !keysByKid.isEmpty() && issuer != null;
            long backoff = healthy ? MIN_REFETCH_INTERVAL_MS : MIN_RETRY_WHEN_UNHEALTHY_MS;
            if (lastFetchAttempt != 0L && now - lastFetchAttempt < backoff) {
                return !keysByKid.isEmpty();
            }
            lastFetchAttempt = now;
            try {
                Map<String, PublicKey> fetched = fetchJwks();
                if (fetched.isEmpty()) {
                    logger.warn("Keycloak JWKS at {} held no usable RSA signing keys", jwksUrl);
                } else {
                    keysByKid = fetched;
                    logger.info("Loaded {} Keycloak signing key(s): {}", fetched.size(), fetched.keySet());
                }
            } catch (Exception ex) {
                // Keep whatever we already had: a transient outage must not
                // permanently break auth the way the old one-shot fetch did.
                logger.error("Failed to fetch Keycloak JWKS from {}: {}", jwksUrl, ex.toString());
            }
            // H2: the realm's own advertised issuer. Fetched alongside the keys so
            // it shares their caching/throttle/retry, and kept on failure for the
            // same reason.
            try {
                String fetchedIssuer = fetchIssuer();
                if (fetchedIssuer != null && !fetchedIssuer.isBlank()) {
                    if (!fetchedIssuer.equals(issuer)) {
                        logger.info("Keycloak realm issuer: {}", fetchedIssuer);
                    }
                    issuer = fetchedIssuer;
                }
            } catch (Exception ex) {
                logger.error("Failed to fetch OIDC discovery from {}: {}", discoveryUrl, ex.toString());
            }
            return !keysByKid.isEmpty();
        }
    }

    /** The {@code issuer} the realm advertises in its discovery document. */
    private String fetchIssuer() throws Exception {
        JsonObject discovery = Json.createReader(new StringReader(get(discoveryUrl))).readObject();
        return discovery.getString("issuer", null);
    }

    private Map<String, PublicKey> fetchJwks() throws Exception {
        return parseJwks(get(jwksUrl));
    }

    /** GET a JSON document from the realm, honouring the server's SSL context. */
    private String get(String target) throws Exception {
        URL url = new URI(target).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            if (connection instanceof HttpsURLConnection httpsConnection) {
                SSLContext ctx = SslConfig.getSslContext();
                if (ctx != null) {
                    httpsConnection.setSSLSocketFactory(ctx.getSocketFactory());
                }
            }
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            // The old code had no timeouts: a hung Keycloak pinned the caller.
            connection.setConnectTimeout(HTTP_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_TIMEOUT_MS);
            if (connection.getResponseCode() != 200) {
                throw new IOException("fetch of " + target + " failed: HTTP " + connection.getResponseCode()
                        + " " + connection.getResponseMessage());
            }
            try (InputStream in = connection.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Every usable RSA <em>signature</em> key in a JWKS document, indexed by kid.
     * <p>
     * A realm also publishes an RSA-OAEP <em>encryption</em> key ({@code use=enc}),
     * which must never be used to verify a signature, so it is skipped. Keys that
     * are not RSA, carry no {@code kid}, or fail to decode are skipped rather than
     * failing the whole document — one bad JWK must not take out a good one.
     * Package-private for unit testing.
     */
    static Map<String, PublicKey> parseJwks(String json) {
        Map<String, PublicKey> map = new LinkedHashMap<>();
        JsonObject root = Json.createReader(new StringReader(json)).readObject();
        JsonArray keys = root.getJsonArray("keys");
        if (keys == null) {
            return map;
        }
        for (int i = 0; i < keys.size(); i++) {
            JsonObject jwk = keys.getJsonObject(i);
            String kty = jwk.getString("kty", null);
            String use = jwk.getString("use", null);
            String kid = jwk.getString("kid", null);
            if (!"RSA".equals(kty) || "enc".equals(use) || kid == null) {
                continue;
            }
            String n = jwk.getString("n", null);
            String e = jwk.getString("e", null);
            if (n == null || e == null) {
                continue;
            }
            try {
                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
                map.put(kid, KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(modulus, exponent)));
            } catch (RuntimeException | java.security.GeneralSecurityException ex) {
                logger.warn("Skipping unusable JWK kid={}: {}", kid, ex.toString());
            }
        }
        return map;
    }
}
