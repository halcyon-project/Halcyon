package com.ebremer.lws.auth.oidc;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves an OpenID Provider's signing key for LWS-OIDC verification: discover the issuer,
 * confirm it self-identifies as that issuer, fetch its JWKS, and return the public key for a
 * given {@code kid} — pinned to the token's algorithm.
 *
 * <p>Unlike the same-box Keycloak {@code JwksCache}, the issuer here is any OP a WebID's CID
 * vouches for, so fetches use <strong>normal TLS validation</strong> (a real CA chain), are
 * {@link SsrfGuard}-checked, and never follow redirects (a redirect could point back inside
 * the network). Discovery is cached per issuer and the JWKS is refreshed on an unknown
 * {@code kid} (rate-limited) so a live realm can rotate keys without a restart, and a flood of
 * unknown-kid tokens cannot be turned into a request amplifier.
 *
 * <p>The algorithm/key pin ({@link #algMatchesKey}) is the algorithm-confusion defense: a
 * symmetric ({@code HS*}), {@code none}, or unknown {@code alg} never matches an RSA/EC key, so
 * an OP's public key can never be misused as an HMAC secret. RSA and EC (P-256/384/521) keys
 * are supported; other key types are skipped.
 *
 * <p>Ported from {@code LWSCredentialVerifier.resolveSigningKey} in the lws-authn extension.
 */
public final class OidcKeys {

    private static final Logger LOG = LoggerFactory.getLogger(OidcKeys.class);

    /** Shortest interval between two JWKS refreshes prompted by an unknown kid. */
    private static final long MIN_REFRESH_MS = 30_000;

    /** An OP whose {@code jwks_uri} has been discovered, with its keys cached by kid. */
    private static final class Issuer {
        final String jwksUri;
        final AtomicLong lastFetch = new AtomicLong(0);
        volatile Map<String, PublicKey> keys = Map.of();

        Issuer(String jwksUri) {
            this.jwksUri = jwksUri;
        }
    }

    /** OIDC discovery, JWKS resolution, or key parsing failed for an issuer. */
    public static final class OidcException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public OidcException(String message) {
            super(message);
        }
    }

    private final HttpClient http;
    private final Duration timeout;
    private final Map<String, Issuer> issuers = new ConcurrentHashMap<>();

    public OidcKeys() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), Duration.ofSeconds(10));
    }

    OidcKeys(HttpClient http, Duration timeout) {
        this.http = http;
        this.timeout = timeout;
    }

    /**
     * The public key that signs {@code iss}'s tokens for {@code kid}, pinned to {@code alg}.
     *
     * @throws SsrfGuard.BlockedException if a discovery/JWKS URL is not a fetchable external URL
     * @throws OidcException              on discovery failure, issuer mismatch, missing key, or alg/key mismatch
     */
    public PublicKey signingKey(String iss, String kid, String alg, Set<String> allowedHosts) {
        if (kid == null || kid.isBlank()) {
            throw new OidcException("token has no kid; cannot select a signing key for " + iss);
        }
        Issuer issuer = issuers.computeIfAbsent(iss, i -> discover(i, allowedHosts));
        PublicKey key = issuer.keys.get(kid);
        if (key == null) {
            long now = System.currentTimeMillis();
            long last = issuer.lastFetch.get();
            if (now - last >= MIN_REFRESH_MS && issuer.lastFetch.compareAndSet(last, now)) {
                refresh(issuer, allowedHosts);
                key = issuer.keys.get(kid);
            }
        }
        if (key == null) {
            throw new OidcException("no signing key for kid=" + kid + " at issuer " + iss);
        }
        if (!algMatchesKey(alg, key)) {
            throw new OidcException("token alg " + alg + " is not consistent with the "
                    + key.getAlgorithm() + " signing key");
        }
        return key;
    }

    private Issuer discover(String iss, Set<String> allowedHosts) {
        String base = iss.endsWith("/") ? iss.substring(0, iss.length() - 1) : iss;
        String url = base + "/.well-known/openid-configuration";
        SsrfGuard.verify(url, allowedHosts);
        JsonObject cfg = getJson(url);
        String discovered = cfg.getString("issuer", null);
        if (!iss.equals(discovered)) {
            throw new OidcException("OIDC discovery issuer mismatch: expected <" + iss
                    + ">, discovery says <" + discovered + ">");
        }
        String jwksUri = cfg.getString("jwks_uri", null);
        if (jwksUri == null || jwksUri.isBlank()) {
            throw new OidcException("OIDC discovery for <" + iss + "> has no jwks_uri");
        }
        return new Issuer(jwksUri);
    }

    private void refresh(Issuer issuer, Set<String> allowedHosts) {
        SsrfGuard.verify(issuer.jwksUri, allowedHosts);
        JsonObject doc = getJson(issuer.jwksUri);
        JsonArray keys = doc.getJsonArray("keys");
        if (keys == null) {
            return;
        }
        Map<String, PublicKey> fresh = new HashMap<>();
        for (JsonObject jwk : keys.getValuesAs(JsonObject.class)) {
            String kid = jwk.getString("kid", null);
            if (kid == null) {
                continue;
            }
            try {
                fresh.put(kid, toPublicKey(jwk));
            } catch (GeneralSecurityException | RuntimeException e) {
                LOG.debug("skipping unusable JWK {}: {}", kid, e.toString());
            }
        }
        if (!fresh.isEmpty()) {
            issuer.keys = Map.copyOf(fresh);
        }
    }

    private JsonObject getJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                throw new OidcException("GET " + url + " returned HTTP " + resp.statusCode());
            }
            try (InputStream in = resp.body(); JsonReader r = Json.createReader(in)) {
                return r.readObject();
            }
        } catch (IOException e) {
            throw new OidcException("could not fetch " + url + ": " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OidcException("interrupted fetching " + url);
        }
    }

    private static PublicKey toPublicKey(JsonObject jwk) throws GeneralSecurityException {
        String kty = jwk.getString("kty", null);
        if ("RSA".equals(kty)) {
            return KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(uint(jwk.getString("n")), uint(jwk.getString("e"))));
        }
        if ("EC".equals(kty)) {
            String std = switch (jwk.getString("crv", "")) {
                case "P-256" -> "secp256r1";
                case "P-384" -> "secp384r1";
                case "P-521" -> "secp521r1";
                default -> null;
            };
            if (std == null) {
                throw new OidcException("unsupported EC curve: " + jwk.getString("crv", null));
            }
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec(std));
            ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);
            ECPoint w = new ECPoint(uint(jwk.getString("x")), uint(jwk.getString("y")));
            return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, ecSpec));
        }
        throw new OidcException("unsupported JWK kty: " + kty);
    }

    private static BigInteger uint(String base64url) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(base64url));
    }

    /**
     * True iff {@code alg} is an asymmetric signature algorithm whose key type matches
     * {@code key}. Symmetric ({@code HS*}), {@code none} and unknown algorithms never match.
     */
    static boolean algMatchesKey(String alg, PublicKey key) {
        if (alg == null || key == null) {
            return false;
        }
        String keyType = key.getAlgorithm();
        if (alg.startsWith("RS") || alg.startsWith("PS")) {
            return "RSA".equals(keyType);
        }
        if (alg.startsWith("ES")) {
            return "EC".equals(keyType) || "ECDSA".equals(keyType);
        }
        return false;
    }
}
