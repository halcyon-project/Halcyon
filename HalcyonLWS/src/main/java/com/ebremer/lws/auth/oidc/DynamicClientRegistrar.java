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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenID Connect Dynamic Client Registration (RFC 7591): registers this resource server as a client
 * at an OP discovered from a WebID, so interactive WebID login works with <em>any</em> conformant OP
 * without a pre-arranged {@code client_id}.
 *
 * <p>The registered client is public (PKCE, {@code token_endpoint_auth_method: none}), authorization
 * code only, with the storage's {@code /webid-callback} as its sole redirect URI. The resulting
 * {@code client_id} is cached per issuer so a login does not register a fresh client every time. The
 * registration endpoint is {@link SsrfGuard}-checked like every other outbound call.
 *
 * <p>Note: registration only gives a {@code client_id}; the login still requires the OP to assert the
 * WebID in the ID token (as {@code sub} or a {@code webid} claim) — see {@link WebIdOidcLogin}. For a
 * Keycloak OP that means the WebID mapper on a default client scope, and a realm that permits
 * anonymous registration.
 */
public final class DynamicClientRegistrar {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicClientRegistrar.class);

    /** Dynamic registration was refused or malformed. */
    public static final class RegistrationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public RegistrationException(String message) {
            super(message);
        }
    }

    private final Map<String, String> clientIdByIssuer = new ConcurrentHashMap<>();

    /** The client id to use at {@code issuer}, registering once (then cached) if not already known. */
    public String clientId(String issuer, String registrationEndpoint, String redirectUri,
            Set<String> allowedHosts, HttpClient http, Duration timeout) {
        return clientIdByIssuer.computeIfAbsent(issuer,
                i -> register(registrationEndpoint, redirectUri, allowedHosts, http, timeout));
    }

    private String register(String registrationEndpoint, String redirectUri, Set<String> allowedHosts,
            HttpClient http, Duration timeout) {
        if (registrationEndpoint == null || registrationEndpoint.isBlank()) {
            throw new RegistrationException("the OpenID Provider advertises no dynamic registration endpoint");
        }
        SsrfGuard.verify(registrationEndpoint, allowedHosts);
        String body = Json.createObjectBuilder()
                .add("application_type", "web")
                .add("client_name", "Halcyon WebID Login")
                .add("token_endpoint_auth_method", "none")
                .add("grant_types", Json.createArrayBuilder().add("authorization_code"))
                .add("response_types", Json.createArrayBuilder().add("code"))
                .add("redirect_uris", Json.createArrayBuilder().add(redirectUri))
                .build().toString();
        try {
            HttpResponse<InputStream> resp = http.send(
                    HttpRequest.newBuilder(URI.create(registrationEndpoint)).timeout(timeout)
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200 && resp.statusCode() != 201) {
                throw new RegistrationException("dynamic registration at " + registrationEndpoint
                        + " returned HTTP " + resp.statusCode());
            }
            try (InputStream in = resp.body(); JsonReader r = Json.createReader(in)) {
                JsonObject o = r.readObject();
                String clientId = o.getString("client_id", null);
                if (clientId == null || clientId.isBlank()) {
                    throw new RegistrationException("dynamic registration response carried no client_id");
                }
                LOG.info("dynamically registered WebID-login client {} at {}", clientId, registrationEndpoint);
                return clientId;
            }
        } catch (IOException e) {
            throw new RegistrationException("dynamic registration failed: " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RegistrationException("interrupted during dynamic registration");
        }
    }
}
