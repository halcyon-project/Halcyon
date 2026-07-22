package com.ebremer.lws.auth.oidc;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/**
 * The OpenID Provider endpoints an interactive WebID login needs: the authorization and token
 * endpoints (to drive the Authorization-Code flow) plus the JWKS URI. Obtained by OIDC discovery
 * on an issuer that a WebID's CID vouched for — SSRF-guarded, and the discovered {@code issuer}
 * must match the one asked for (the OP must self-identify).
 *
 * <p>Distinct from {@link OidcKeys}, which caches JWKS for verification; this is a one-shot fetch
 * of the endpoints the login redirect and code exchange are built from.
 */
public record OidcDiscovery(String issuer, String authorizationEndpoint, String tokenEndpoint,
        String jwksUri, String registrationEndpoint) {

    public static OidcDiscovery fetch(String issuer, HttpClient http, Duration timeout, Set<String> allowedHosts) {
        String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        String url = base + "/.well-known/openid-configuration";
        SsrfGuard.verify(url, allowedHosts);
        JsonObject cfg;
        try {
            HttpResponse<InputStream> resp = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                            .header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                throw new OidcKeys.OidcException("OIDC discovery " + url + " returned HTTP " + resp.statusCode());
            }
            try (InputStream in = resp.body(); JsonReader r = Json.createReader(in)) {
                cfg = r.readObject();
            }
        } catch (IOException e) {
            throw new OidcKeys.OidcException("OIDC discovery fetch failed for " + issuer + ": " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OidcKeys.OidcException("interrupted during discovery for " + issuer);
        }
        String discovered = cfg.getString("issuer", null);
        if (!issuer.equals(discovered)) {
            throw new OidcKeys.OidcException("OIDC discovery issuer mismatch: expected <" + issuer
                    + ">, discovery says <" + discovered + ">");
        }
        String authz = cfg.getString("authorization_endpoint", null);
        String token = cfg.getString("token_endpoint", null);
        String jwks = cfg.getString("jwks_uri", null);
        if (authz == null || token == null) {
            throw new OidcKeys.OidcException("OIDC discovery for <" + issuer + "> lacks an authorization or token endpoint");
        }
        String registration = cfg.getString("registration_endpoint", null);
        return new OidcDiscovery(issuer, authz, token, jwks, registration);
    }
}
