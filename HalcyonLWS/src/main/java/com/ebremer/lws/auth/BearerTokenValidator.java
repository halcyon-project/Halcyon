package com.ebremer.lws.auth;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.http.Problem;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Key;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates the {@code Authorization: Bearer} access token and resolves the agent.
 *
 * <p>Implements the checks lws10-core makes mandatory: verify the signature against
 * the authorization server's published keys, verify {@code iss}, verify that
 * {@code aud} names this storage, and verify the token is temporally valid — with
 * the request rejected on any failure.
 *
 * <p>The {@code aud} check is the one that is easy to skip and expensive to omit.
 * Without it, a token minted for <em>any</em> audience in the realm — another
 * service, another storage — is accepted here. Halcyon's existing Fuseki
 * {@code JwtVerifier} validates only the signature and expiry and is precisely that
 * bug; this does not copy it.
 */
public final class BearerTokenValidator {

    private static final Logger LOG = LoggerFactory.getLogger(BearerTokenValidator.class);

    /** Tolerance for clock drift between this host and the authorization server. */
    private static final long SKEW_SECONDS = 60;

    private final LwsStorageConfig cfg;
    private final String issuer;
    private final JwksCache jwks;

    public BearerTokenValidator(LwsStorageConfig cfg) {
        this.cfg = cfg;
        HalcyonSettings hs = HalcyonSettings.getSettings();
        String base = hs.getAuthServer() + "/realms/" + realm();

        // Ask the authorization server what it calls itself, rather than assuming.
        // Keycloak stamps `iss` from the host a token was requested through, so a
        // constructed issuer is right only by luck: fetched via Halcyon's /auth proxy
        // it says https://localhost:8888/..., fetched directly it says
        // http://localhost:8080/... . Discovery is the authoritative answer, and it
        // also yields the jwks_uri instead of us hardcoding Keycloak's path layout.
        Discovered d = discover(base);
        this.issuer = d.issuer();
        this.jwks = new JwksCache(d.jwksUri());
        LOG.info("LWS storage {} trusts issuer {} (jwks {})",
                cfg.urlPath(), d.issuer(), d.jwksUri());
    }

    private record Discovered(String issuer, String jwksUri) {
    }

    private static Discovered discover(String base) {
        String wellKnown = base + "/.well-known/openid-configuration";
        try {
            java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .sslContext(JwksCache.trustAll())
                    .build();
            var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(wellKnown))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET().build();
            var resp = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() == 200) {
                try (var r = jakarta.json.Json.createReader(resp.body())) {
                    var o = r.readObject();
                    String iss = o.getString("issuer", null);
                    String jwks = o.getString("jwks_uri", null);
                    if (iss != null && jwks != null) {
                        return new Discovered(iss, jwks);
                    }
                }
            }
            LOG.warn("OIDC discovery at {} returned {}", wellKnown, resp.statusCode());
        } catch (java.io.IOException | RuntimeException e) {
            LOG.warn("OIDC discovery at {} failed: {}", wellKnown, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Fall back to Keycloak's conventional layout so a transiently unreachable
        // auth server does not stop the storage from starting.
        LOG.warn("falling back to constructed issuer {}", base);
        return new Discovered(base, base + "/protocol/openid-connect/certs");
    }

    /**
     * The Keycloak realm, read from {@code keycloak.json}.
     *
     * <p>Explicitly <em>not</em> {@code HalcyonSettings.getRealm()}, which is a
     * hardcoded {@code "master"} and does not describe this deployment — the realm is
     * {@code Halcyon}, as both {@code keycloak.json} and {@code Cool.java} say. Trusting
     * that accessor would point every client's {@code as_uri} at the wrong
     * authorization server and reject every token that came back from the right one.
     */
    private static String realm() {
        java.io.File f = new java.io.File("keycloak.json");
        if (f.isFile()) {
            try (var in = new java.io.FileInputStream(f);
                    var r = jakarta.json.Json.createReader(in)) {
                String realm = r.readObject().getString("realm", null);
                if (realm != null && !realm.isBlank()) {
                    return realm;
                }
            } catch (java.io.IOException | RuntimeException e) {
                LOG.warn("could not read realm from keycloak.json", e);
            }
        }
        LOG.warn("keycloak.json not readable; assuming realm \"Halcyon\"");
        return "Halcyon";
    }

    /** The authorization server a client should go to for a token. */
    public String authorizationServer() {
        return issuer;
    }

    /**
     * Resolve the requesting agent.
     *
     * <p>No {@code Authorization} header is not an error: LWS resources may be
     * public, and it is the <em>authorization</em> layer that decides. An
     * unauthenticated request becomes {@link AgentContext#PUBLIC} and is then
     * matched against ACP like any other. A malformed or invalid token, on the other
     * hand, is always a 401 — it is an assertion of identity that did not hold up.
     */
    public AgentContext authenticate(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            return AgentContext.PUBLIC;
        }
        if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw unauthorized("invalid_request", "the Authorization scheme must be Bearer");
        }
        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            throw unauthorized("invalid_request", "empty bearer token");
        }

        Claims claims;
        try {
            Jws<Claims> jws = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(ProtectedHeader header) {
                            String kid = header.getKeyId();
                            if (kid == null) {
                                throw new JwtException("token has no kid");
                            }
                            Key k = jwks.key(kid);
                            if (k == null) {
                                throw new JwtException("unknown signing key " + kid);
                            }
                            return k;
                        }
                    })
                    .clockSkewSeconds(SKEW_SECONDS)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token);
            claims = jws.getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            LOG.debug("rejecting bearer token: {}", e.toString());
            throw unauthorized("invalid_token", "the access token is not valid");
        }

        requireAudience(claims);

        return new AgentContext(
                webIdOf(claims),
                claims.get("azp", String.class) != null
                        ? claims.get("azp", String.class)
                        : claims.get("client_id", String.class),
                claims.getIssuer(),
                List.of());
    }

    /**
     * The token must name this storage.
     *
     * <p>lws10-core: "Verify the {@code aud} claim contains ... a URI identifying the
     * storage server which logically contains the target resource." This is the check
     * that is easy to skip and expensive to omit — without it, a token minted for
     * <em>any</em> audience in the realm is accepted here. Halcyon's existing Fuseki
     * {@code JwtVerifier} validates only signature and expiry and is exactly that bug.
     *
     * <p>An audience is accepted when it <em>logically contains</em> the storage: an
     * {@code aud} of {@code https://localhost:8888} covers the storage at
     * {@code https://localhost:8888/W3Clws}, which is what lets one Keycloak audience
     * mapper serve every storage on the instance. Keycloak does not put the resource
     * server in {@code aud} by default, so that mapper is a deployment requirement,
     * not an optional nicety.
     *
     * <p>Deviation, deliberately: the spec asks that {@code aud} contain "exactly one
     * value", and Keycloak always emits several (for example {@code realm-management},
     * {@code broker}). Requiring exactly one would reject every token Keycloak can
     * issue. Requiring that <em>some</em> audience covers this storage preserves the
     * property the rule exists for — a token minted for a different service will not
     * be accepted here.
     */
    private void requireAudience(Claims claims) {
        var aud = claims.getAudience();
        if (aud == null || aud.isEmpty()) {
            throw unauthorized("invalid_token", "the access token names no audience");
        }
        String storage = cfg.realm();
        boolean ok = aud.stream().anyMatch(a -> a != null && !a.isBlank()
                && (storage.equals(a) || storage.startsWith(stripSlash(a) + "/")));
        if (!ok) {
            LOG.debug("token audience {} does not cover storage {}", aud, storage);
            throw unauthorized("invalid_token",
                    "the access token was not issued for this storage");
        }
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * The agent's WebID.
     *
     * <p>Preferred from a {@code webid} claim, which is what Keycloak will carry once
     * a protocol mapper exposes the user attribute Halcyon already stores. Until
     * then, derive a stable URI from the username so that policies can be written and
     * enforced today, and keep working unchanged once the mapper lands.
     *
     * <p>Note what is <em>not</em> used: Keycloak's {@code sub} is an opaque UUID, not
     * a URI, and would make every ACP policy meaningless outside this one realm.
     */
    private static String webIdOf(Claims claims) {
        String webid = claims.get("webid", String.class);
        if (webid != null && !webid.isBlank()) {
            return webid;
        }
        String user = claims.get("preferred_username", String.class);
        if (user == null || user.isBlank()) {
            user = claims.getSubject();
        }
        if (user == null || user.isBlank()) {
            return null;
        }
        return HalcyonSettings.getSettings().getProxyHostName()
                + "/user/" + user.toLowerCase(Locale.ROOT) + "#me";
    }

    /**
     * The 401 challenge, which is how a client discovers where to get a token.
     *
     * <p>{@code as_uri} names the authorization server and {@code realm} the scope of
     * protection — both REQUIRED, and together they let a client authenticate without
     * a single hardcoded URI.
     *
     * <p>{@code error} is omitted when the request carried no credentials at all. RFC
     * 6750 is explicit: "If the request lacks any authentication information ... the
     * authorization server SHOULD NOT include an error code". An error code there would
     * be describing a failure that never happened — nothing was presented to reject.
     */
    public Problem unauthorized(String error, String detail) {
        StringBuilder challenge = new StringBuilder("Bearer as_uri=\"").append(issuer)
                .append("\", realm=\"").append(cfg.realm()).append('"');
        if (error != null && !error.isBlank()) {
            challenge.append(", error=\"").append(error).append('"');
        }
        return Problem.unauthorized(detail)
                .header("WWW-Authenticate", challenge.toString())
                // Even on a 401, tell the client where the storage describes itself.
                .header("Link", "<" + cfg.descriptionUri() + ">; rel=\""
                        + com.ebremer.lws.vocab.LWS.REL_STORAGE_DESCRIPTION + "\"");
    }

    /** Challenge for a request that offered no credentials. */
    public Problem unauthenticated(String detail) {
        return unauthorized(null, detail);
    }
}
