package com.ebremer.halcyon.lws;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * An LWS <em>client</em>. The Storage UI talks to the storage exactly the way any other
 * agent would.
 *
 * <p>This indirection is the point, not an accident. The Wicket pages run in the same JVM
 * as the storage and could reach into {@code LwsStore} directly — and that is precisely
 * what they must not do. Going over HTTP with the LWS media types means the UI exercises
 * the same protocol, the same content negotiation, the same bearer token and the same ACP
 * decisions as an external client, so a bug that would break a third-party agent breaks
 * the UI too, visibly, instead of being papered over by privileged access.
 *
 * <p>It also keeps the dependency honest: Halcyon depends on the HalcyonLWS jar only to
 * mount the servlet, never to read its data.
 */
public final class LwsClient implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String LWS_JSON = "application/lws+json";
    private static final String LWS_QUERY_JSON = "application/lws-query+json";

    private final transient HttpClient http;
    private final String bearer;
    private final String tokenOrigin;

    /**
     * @param bearerToken the signed-in user's access token, or {@code null} for anonymous.
     * @param tokenOrigin the origin ({@code scheme://host:port}) the token is valid for — the
     *     local Halcyon site. The token is attached <strong>only</strong> to requests to that
     *     origin. A federated storage is a third-party server on another origin, and sending it
     *     this token would hand a stranger the user's local credential; those requests go out
     *     anonymously instead. Pass {@code null} to attach the token to no request at all.
     */
    public LwsClient(String bearerToken, String tokenOrigin) {
        this.bearer = bearerToken;
        this.tokenOrigin = origin(tokenOrigin);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .sslContext(trustLoopback())
                .build();
    }

    /** What a request came back with, without throwing on a 4xx the UI wants to render. */
    public record Result(int status, JsonObject body, String location) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    public Result get(String uri) {
        return send(builder(uri).GET().header("Accept", LWS_JSON).build());
    }

    /** Create a resource. A null {@code slug} lets the server name it. */
    public Result post(String container, String slug, String contentType, byte[] body,
            boolean asContainer) {
        HttpRequest.Builder b = builder(container)
                .header("Accept", LWS_JSON)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body));
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        if (slug != null && !slug.isBlank()) {
            b.header("Slug", slug);
        }
        if (asContainer) {
            b.header("Link", "<https://www.w3.org/ns/lws#Container>; rel=\"type\"");
        }
        return send(b.build());
    }

    /**
     * Delete a resource. The entity tag is required, not optional — the storage answers
     * 428 to an unconditional delete, which is exactly the protection the UI wants.
     */
    public Result delete(String uri, String etag, boolean recursive) {
        HttpRequest.Builder b = builder(uri).DELETE().header("Accept", LWS_JSON);
        if (etag != null) {
            b.header("If-Match", etag);
        }
        if (recursive) {
            b.header("Depth", "infinity");
        }
        return send(b.build());
    }

    /**
     * A Type Search, driven by HTTP {@code QUERY} (RFC 10008).
     *
     * <p>The JDK's {@code HttpClient} accepts an arbitrary method here, which is what
     * makes it possible to be a conformant Type Search client with no extra dependency —
     * and it is worth noting that a Spring {@code RestClient} could not do this, for the
     * same reason the server could not be a {@code @RestController}.
     */
    public Result query(String searchEndpoint, String filterJson) {
        return send(builder(searchEndpoint)
                .header("Accept", LWS_JSON)
                .header("Content-Type", LWS_QUERY_JSON)
                .method("QUERY", HttpRequest.BodyPublishers.ofString(filterJson))
                .build());
    }

    /** The entity tag of a resource, needed for any conditional write. */
    public String etag(String uri) {
        try {
            HttpResponse<Void> r = http.send(
                    builder(uri).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.discarding());
            return r.headers().firstValue("ETag").orElse(null);
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private HttpRequest.Builder builder(String uri) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(60));
        if (bearer != null && !bearer.isBlank() && tokenOrigin != null
                && tokenOrigin.equals(origin(uri))) {
            b.header("Authorization", "Bearer " + bearer);
        }
        return b;
    }

    /**
     * The origin of a URI as {@code scheme://host:port} (lower-cased, with the scheme's default
     * port made explicit), or {@code null} if it has no host. Two URIs share an origin iff their
     * origins are equal — which is the test for whether the local token may travel with a request.
     */
    static String origin(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        try {
            URI u = URI.create(uri.trim());
            String scheme = u.getScheme();
            String host = u.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            scheme = scheme.toLowerCase();
            int port = u.getPort();
            if (port < 0) {
                port = "https".equals(scheme) ? 443 : "http".equals(scheme) ? 80 : -1;
            }
            return scheme + "://" + host.toLowerCase() + ":" + port;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Result send(HttpRequest req) {
        try {
            HttpResponse<byte[]> r = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            JsonObject body = null;
            if (r.body() != null && r.body().length > 0) {
                try (JsonReader jr = Json.createReader(new ByteArrayInputStream(r.body()))) {
                    body = jr.readObject();
                } catch (RuntimeException ignored) {
                    // Not JSON (a binary resource, or an empty 204). Nothing to render.
                }
            }
            return new Result(r.statusCode(), body,
                    r.headers().firstValue("Location").orElse(null));
        } catch (IOException e) {
            return new Result(0, error(e.getMessage()), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(0, error("interrupted"), null);
        }
    }

    private static JsonObject error(String detail) {
        return Json.createObjectBuilder()
                .add("title", "the storage could not be reached")
                .add("detail", String.valueOf(detail))
                .build();
    }

    /** Halcyon serves itself on a self-signed certificate; the UI loops back to it. */
    private static SSLContext trustLoopback() {
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

    public static String utf8(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}
