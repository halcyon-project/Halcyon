package com.ebremer.lws.auth.cid;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.auth.CredentialVerifier;
import com.ebremer.lws.auth.InvalidBearerTokenException;
import com.ebremer.lws.auth.PresentedToken;
import com.ebremer.lws.auth.oidc.SsrfGuard;
import com.ebremer.lws.auth.oidc.TrustPolicy;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The LWS authentication suite for self-signed identity using controlled identifiers
 * (<a href="https://www.w3.org/TR/lws10-authn-ssi-cid/">lws10-authn-ssi-cid</a>): a JWT the subject
 * signs itself, presented with subject token type {@code urn:ietf:params:oauth:token-type:jwt}.
 *
 * <p>The credential's {@code sub}, {@code iss} and {@code client_id} are one URI; it is not signed
 * with {@code none}; it carries {@code exp} and {@code iat}, and an {@code aud} that includes this
 * verifier's audience (the authorization server for a token exchange, the storage for a credential
 * presented directly). The verifier dereferences the subject to its controlled identifier document,
 * whose {@code id} must be the subject, and uses the JWT's {@code kid} to select a verification
 * method (CID 1.0 section 3.3):
 * <ul>
 *   <li>only methods the {@code authentication} relationship names count, embedded in it or
 *       referenced from it and defined in the same document;</li>
 *   <li>a method must be controlled by the subject and live in the subject's document;</li>
 *   <li>a {@code JsonWebKey} carries a {@code publicKeyJwk} with no private members, a
 *       {@code Multikey} a {@code publicKeyMultibase}, the two types CID 1.0 defines (their Security
 *       Vocabulary predecessors {@code JsonWebKey2020} and {@code Ed25519VerificationKey2020} are
 *       read the same way);</li>
 *   <li>a method past its {@code revoked} or {@code expires} time cannot be used.</li>
 * </ul>
 *
 * <p><b>Subjects.</b> An HTTPS subject is fetched, SSRF-checked and size-capped, as the OpenID
 * suite's documents are. A DID subject is resolved by its method: {@code did:key} locally, with
 * nothing to fetch, and {@code did:web} from the HTTPS URL it names. Any other DID method is
 * refused. Documents are cached for a few minutes, and failures for less, because an unauthenticated
 * client chooses the URL: without a cache each exchange would be an outbound request to an address of
 * the caller's choosing. The deployment's WebID-host policy applies to HTTPS and did:web subjects.
 *
 * <p><b>Routing.</b> The suite claims a credential whose subject is a DID, or whose subject is an
 * HTTPS URL equal to its issuer (self-issued). An OpenID credential names a separate provider as its
 * issuer, so the two suites never claim the same credential.
 *
 * <p>Ported from lws-server's {@code SsiCidValidator}, which implements the same suite.
 */
public final class SelfIssuedCidVerifier implements CredentialVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(SelfIssuedCidVerifier.class);

    private static final long SKEW_SECONDS = 60;
    private static final int MAX_DOCUMENT_BYTES = 256 * 1024;
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DOCUMENT_TTL = Duration.ofMinutes(5);
    private static final Duration FAILURE_TTL = Duration.ofSeconds(30);
    private static final int MAX_CACHED = 1000;
    private static final String ACCEPT =
            "application/did+json, application/did+ld+json, application/ld+json;q=0.9, application/json;q=0.8";

    private final Supplier<Set<String>> audiences;
    private final Supplier<Set<String>> allowedInternalHosts;
    private final Supplier<TrustPolicy> webIdHosts;
    private final Clock clock;
    private final HttpClient http;
    private final Map<String, Cached> documents = new ConcurrentHashMap<>();

    private record Cached(JsonObject document, Instant until) {
    }

    /** A verification method the subject may authenticate with. */
    record Method(String id, PublicKey key, String jwkKid, Instant revoked, Instant expires) {
    }

    /**
     * @param audiences            the values one of which a credential's {@code aud} must include
     * @param allowedInternalHosts hosts the SSRF guard lets through although they resolve internally
     * @param webIdHosts           the deployment's WebID-host policy
     */
    public SelfIssuedCidVerifier(Supplier<Set<String>> audiences, Supplier<Set<String>> allowedInternalHosts,
            Supplier<TrustPolicy> webIdHosts) {
        this(audiences, allowedInternalHosts, webIdHosts, Clock.systemUTC(),
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(FETCH_TIMEOUT).build());
    }

    SelfIssuedCidVerifier(Supplier<Set<String>> audiences, Supplier<Set<String>> allowedInternalHosts,
            Supplier<TrustPolicy> webIdHosts, Clock clock, HttpClient http) {
        this.audiences = audiences;
        this.allowedInternalHosts = allowedInternalHosts;
        this.webIdHosts = webIdHosts == null ? () -> TrustPolicy.ALLOW_ALL : webIdHosts;
        this.clock = clock;
        this.http = http;
    }

    @Override
    public AgentContext tryAuthenticate(PresentedToken token, HttpServletRequest req) {
        String sub = token.sub();
        if (!claims(sub, token.iss())) {
            return null;   // an OpenID credential, or not an LWS credential at all
        }
        if (token.alg() == null || "none".equalsIgnoreCase(token.alg())) {
            throw refuse("a self-issued credential must be signed (alg is 'none')");
        }
        if (token.kid() == null || token.kid().isBlank()) {
            throw refuse("a self-issued credential names its verification method with kid");
        }
        JsonObject document = subjectDocument(sub);
        Method method = select(collect(document, sub), token.kid());
        if (method == null) {
            throw refuse("no authentication method of <" + sub + "> matches kid " + token.kid());
        }
        Instant now = clock.instant();
        if (method.revoked() != null && !now.isBefore(method.revoked())
                || method.expires() != null && !now.isBefore(method.expires())) {
            throw refuse("verification method " + method.id() + " is revoked or expired");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(method.key())
                    .clockSkewSeconds(SKEW_SECONDS)
                    .clock(() -> java.util.Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token.raw())
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            LOG.debug("self-issued credential for <{}> does not verify: {}", sub, e.toString());
            throw refuse("the self-issued credential is not valid");
        }
        // Every claim of the data model (validation-all-claims), now that they are signature-covered.
        Object clientId = claims.get("client_id");
        if (!sub.equals(claims.getSubject()) || !sub.equals(claims.getIssuer()) || !sub.equals(clientId)) {
            throw refuse("sub, iss and client_id must all be the same URI");
        }
        if (claims.getExpiration() == null) {
            throw refuse("a self-issued credential carries exp");
        }
        if (claims.getIssuedAt() == null
                || claims.getIssuedAt().toInstant().isAfter(now.plusSeconds(SKEW_SECONDS))) {
            throw refuse("a self-issued credential carries an iat that is not in the future");
        }
        Set<String> aud = claims.getAudience();
        Set<String> accepted = audiences.get();
        if (aud == null || aud.stream().noneMatch(accepted::contains)) {
            throw refuse("the credential's aud does not include " + accepted);
        }
        return new AgentContext(sub, sub, sub, List.of());
    }

    /** Whether this suite owns the credential: a DID subject, or an HTTPS subject that is its own issuer. */
    static boolean claims(String sub, String iss) {
        if (sub == null) {
            return false;
        }
        if (Dids.isDid(sub)) {
            return true;
        }
        return sub.toLowerCase(Locale.ROOT).startsWith("https://") && sub.equals(iss);
    }

    // ---- resolution ----

    private JsonObject subjectDocument(String sub) {
        if (Dids.isDid(sub)) {
            String method;
            try {
                method = Dids.methodOf(sub);
            } catch (IllegalArgumentException e) {
                throw refuse("<" + sub + "> is not a valid DID");
            }
            switch (method) {
                case Dids.KEY -> {
                    try {
                        return Dids.didKeyDocument(sub);
                    } catch (IllegalArgumentException | java.security.GeneralSecurityException e) {
                        throw refuse("unusable did:key: " + e.getMessage());
                    }
                }
                case Dids.WEB -> {
                    String url;
                    try {
                        url = Dids.didWebUrl(sub);
                    } catch (IllegalArgumentException e) {
                        throw refuse("invalid did:web: " + e.getMessage());
                    }
                    return fetch(sub, url);
                }
                default -> throw refuse("did:" + method + " is not a DID method this server resolves");
            }
        }
        return fetch(sub, sub);
    }

    private JsonObject fetch(String subject, String url) {
        try {
            webIdHosts.get().require("WebID host", url);
        } catch (TrustPolicy.RefusedException e) {
            throw refuse(e.getMessage());
        }
        Instant now = clock.instant();
        Cached cached = documents.get(subject);
        if (cached != null && now.isBefore(cached.until())) {
            if (cached.document() == null) {
                throw refuse("the controlled identifier document of <" + subject + "> could not be had");
            }
            return cached.document();
        }
        JsonObject doc = load(url);
        if (documents.size() >= MAX_CACHED) {
            documents.clear();
        }
        documents.put(subject, new Cached(doc, now.plus(doc == null ? FAILURE_TTL : DOCUMENT_TTL)));
        if (doc == null) {
            throw refuse("the controlled identifier document of <" + subject + "> could not be had");
        }
        return doc;
    }

    /** The JSON document at {@code url}, SSRF-checked, size-capped and not redirected; null when not had. */
    private JsonObject load(String url) {
        try {
            SsrfGuard.verify(url, allowedInternalHosts.get());
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(FETCH_TIMEOUT).header("Accept", ACCEPT).GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    return null;
                }
                byte[] body = in.readNBytes(MAX_DOCUMENT_BYTES + 1);
                if (body.length > MAX_DOCUMENT_BYTES) {
                    return null;
                }
                try (JsonReader reader = Json.createReader(new StringReader(new String(body, StandardCharsets.UTF_8)))) {
                    return reader.readObject();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception | StackOverflowError e) {
            // StackOverflowError too: a few kilobytes of nested arrays from a caller-chosen URL
            // would otherwise overflow the parser.
            LOG.debug("could not load controlled identifier document {}: {}", url, e.toString());
            return null;
        }
    }

    // ---- verification methods (CID 1.0 section 3.3) ----

    /** The usable methods the document's {@code authentication} relationship names; none if its id is not the subject. */
    static List<Method> collect(JsonObject doc, String sub) {
        List<Method> out = new ArrayList<>();
        if (doc == null || !sub.equals(string(doc, "id"))) {
            return out;
        }
        JsonValue authentication = doc.get("authentication");
        if (authentication == null) {
            return out;
        }
        List<JsonValue> entries = authentication.getValueType() == JsonValue.ValueType.ARRAY
                ? authentication.asJsonArray() : List.of(authentication);
        Set<String> seen = new HashSet<>();
        for (JsonValue entry : entries) {
            JsonObject method = null;
            if (entry.getValueType() == JsonValue.ValueType.STRING) {
                method = findById(doc, resolve(((JsonString) entry).getString(), sub), sub);
            } else if (entry.getValueType() == JsonValue.ValueType.OBJECT) {
                method = entry.asJsonObject();
            }
            Method m = method == null ? null : toMethod(method, sub);
            if (m != null && (m.id() == null || seen.add(m.id()))) {
                out.add(m);
            }
        }
        return out;
    }

    private static JsonObject findById(JsonObject doc, String id, String sub) {
        if (id == null) {
            return null;
        }
        JsonValue methods = doc.get("verificationMethod");
        if (methods == null || methods.getValueType() != JsonValue.ValueType.ARRAY) {
            return null;
        }
        for (JsonValue v : methods.asJsonArray()) {
            if (v.getValueType() == JsonValue.ValueType.OBJECT
                    && id.equals(resolve(string(v.asJsonObject(), "id"), sub))) {
                return v.asJsonObject();
            }
        }
        return null;
    }

    private static Method toMethod(JsonObject method, String sub) {
        String id = resolve(string(method, "id"), sub);
        String controller = resolve(string(method, "controller"), sub);
        if (!sub.equals(controller) || id == null || !inDocument(id, sub)) {
            return null;
        }
        try {
            Instant revoked = instant(string(method, "revoked"));
            Instant expires = instant(string(method, "expires"));
            String type = string(method, "type");
            if ("JsonWebKey".equals(type) || "JsonWebKey2020".equals(type)) {
                JsonValue jwk = method.get("publicKeyJwk");
                if (jwk == null || jwk.getValueType() != JsonValue.ValueType.OBJECT) {
                    return null;
                }
                return new Method(id, Keys.fromJwk(jwk.asJsonObject()), string(jwk.asJsonObject(), "kid"),
                        revoked, expires);
            }
            if ("Multikey".equals(type) || "Ed25519VerificationKey2020".equals(type)) {
                String multibase = string(method, "publicKeyMultibase");
                return multibase == null ? null : new Method(id, Keys.fromMultikey(multibase), null, revoked, expires);
            }
        } catch (Exception e) {
            // An unreadable revocation date is not evidence the key was never revoked, and an
            // unusable key is not one to verify with: either way the method does not count.
            LOG.debug("skipping verification method {}: {}", id, e.toString());
        }
        return null;
    }

    /**
     * The method the {@code kid} names: by the method's full identifier first (CID 1.0 retrieves by
     * it), then by the JWK's own kid, then by the fragment of the method's identifier, a leading
     * {@code #} on the kid allowed. Never "the only key": the credential says which key signed it.
     */
    static Method select(List<Method> methods, String kid) {
        for (Method m : methods) {
            if (kid.equals(m.id())) {
                return m;
            }
        }
        for (Method m : methods) {
            if (kid.equals(m.jwkKid())) {
                return m;
            }
        }
        String wanted = kid.startsWith("#") ? kid.substring(1) : kid;
        for (Method m : methods) {
            int hash = m.id().lastIndexOf('#');
            if (hash >= 0 && wanted.equals(m.id().substring(hash + 1))) {
                return m;
            }
        }
        return null;
    }

    /** A method id must be the subject's document plus a fragment, or the subject itself. */
    private static boolean inDocument(String methodId, String sub) {
        return methodId.equals(sub) || methodId.startsWith(sub + "#");
    }

    /** A relative reference ({@code #frag}) resolved against the subject; absolute ones unchanged. */
    private static String resolve(String ref, String sub) {
        if (ref == null) {
            return null;
        }
        if (ref.startsWith("#")) {
            int hash = sub.indexOf('#');
            return (hash < 0 ? sub : sub.substring(0, hash)) + ref;
        }
        return ref;
    }

    private static Instant instant(String value) {
        return value == null ? null : OffsetDateTime.parse(value).toInstant();
    }

    private static String string(JsonObject o, String name) {
        JsonValue v = o.get(name);
        return v != null && v.getValueType() == JsonValue.ValueType.STRING ? ((JsonString) v).getString() : null;
    }

    private static InvalidBearerTokenException refuse(String why) {
        LOG.debug("refusing a self-issued credential: {}", why);
        return new InvalidBearerTokenException("invalid_token", why);
    }
}
