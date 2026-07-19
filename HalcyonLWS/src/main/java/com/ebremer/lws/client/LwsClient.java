package com.ebremer.lws.client;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 *
 * <p>MCP-3: the class lives in this module (not the Halcyon app, where it was born)
 * because it is the shared way IN for every surface that acts as the caller — the
 * Wicket storage pages and the MCP tools alike. It stays deliberately free of
 * server-side imports: a client that could see the store would stop being proof
 * the protocol works.
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
    public record Result(int status, JsonObject body, String location, Map<String, String> links) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }

        /**
         * The target of the response's {@code Link} header with this relation type
         * ({@code "first"}, {@code "prev"}, {@code "next"}, {@code "last"}, ...), or
         * {@code null}. Container pagination is carried entirely in these links — the
         * cursors are opaque and HMAC-sealed, so a client follows them rather than
         * building page URIs of its own, and this accessor is how the UI follows.
         */
        public String link(String rel) {
            return links.get(rel);
        }
    }

    public Result get(String uri) {
        return send(builder(uri).GET().header("Accept", LWS_JSON).build());
    }

    /**
     * Headers only — how the UI discovers a resource's {@code rel="acl"} and
     * linkset links without pulling a possibly-huge representation.
     */
    public Result head(String uri) {
        return send(builder(uri)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .header("Accept", LWS_JSON)
                .build());
    }

    /**
     * A raw (non-JSON) representation with its validator — the shape of the ACP
     * access control resource, which the storage serves only as Turtle.
     */
    public record Text(int status, String body, String etag, Map<String, String> links) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }

        public String link(String rel) {
            return links.get(rel);
        }
    }

    /** GET a representation verbatim under an explicit {@code Accept}. */
    public Text getText(String uri, String accept) {
        try {
            HttpResponse<byte[]> r = http.send(
                    builder(uri).GET().header("Accept", accept).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return new Text(r.statusCode(),
                    r.body() == null ? "" : new String(r.body(), StandardCharsets.UTF_8),
                    r.headers().firstValue("ETag").orElse(null),
                    parseLinks(r.headers().allValues("Link")));
        } catch (IOException e) {
            return new Text(0, "the storage could not be reached: " + e.getMessage(), null, Map.of());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Text(0, "interrupted", null, Map.of());
        }
    }

    /**
     * A representation opened for streaming: status, negotiated content type,
     * declared length ({@code -1} when unknown), and the open body. The caller
     * owns the stream and must close it — closing early aborts the transfer,
     * which is exactly what a bounded preview wants.
     */
    public record Stream(int status, String contentType, long length, InputStream body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** GET a representation as a stream — how the preview relay avoids buffering blobs. */
    public Stream stream(String uri) {
        try {
            HttpResponse<InputStream> r = http.send(
                    builder(uri).GET().header("Accept", "*/*").build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            return new Stream(r.statusCode(),
                    r.headers().firstValue("Content-Type").orElse("application/octet-stream"),
                    r.headers().firstValueAsLong("Content-Length").orElse(-1),
                    r.body());
        } catch (IOException e) {
            return new Stream(0, "text/plain", -1, InputStream.nullInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Stream(0, "text/plain", -1, InputStream.nullInputStream());
        }
    }

    /** A bounded text preview: at most {@code maxBytes} of the representation. */
    public record Preview(int status, String contentType, String text, boolean truncated) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /**
     * Read at most {@code maxBytes} of a representation as UTF-8 text, then
     * abort the transfer — a text preview of a huge file must not pull the
     * whole file through the JVM.
     */
    public Preview preview(String uri, int maxBytes) {
        Stream s = stream(uri);
        try (InputStream in = s.body()) {
            byte[] buf = in.readNBytes(maxBytes + 1);
            boolean truncated = buf.length > maxBytes;
            String text = new String(truncated ? java.util.Arrays.copyOf(buf, maxBytes) : buf,
                    StandardCharsets.UTF_8);
            return new Preview(s.status(), s.contentType(), text, truncated);
        } catch (IOException e) {
            return new Preview(0, "text/plain", "could not read: " + e.getMessage(), false);
        }
    }

    /**
     * Replace a representation. {@code ifMatch} carries the entity tag of what
     * the caller read — the storage's conditional-write protection, exactly the
     * guard the ACR editor wants against clobbering a concurrent policy change.
     */
    public Result put(String uri, String contentType, byte[] body, String ifMatch) {
        HttpRequest.Builder b = builder(uri)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body))
                .header("Accept", LWS_JSON);
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        if (ifMatch != null) {
            b.header("If-Match", ifMatch);
        }
        return send(b.build());
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
     * Public because callers that federate (the Storage page's added-by-URI storages) make the
     * same same-origin decision when labeling what will be browsed anonymously.
     */
    public static String origin(String uri) {
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
                    r.headers().firstValue("Location").orElse(null),
                    parseLinks(r.headers().allValues("Link")));
        } catch (IOException e) {
            return new Result(0, error(e.getMessage()), null, Map.of());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(0, error("interrupted"), null, Map.of());
        }
    }

    // RFC 8288 link-values — the same grammar the storage's own LinkHeader emits and parses.
    private static final Pattern LINK_VALUE = Pattern.compile("<([^>]*)>\\s*((?:;[^,;]*)*)");
    private static final Pattern REL_PARAM = Pattern.compile(
            ";\\s*rel\\s*=\\s*(?:\"([^\"]*)\"|([^;,\\s]+))", Pattern.CASE_INSENSITIVE);

    /**
     * Flatten the response's {@code Link} headers to a rel → target map (first
     * occurrence of a rel wins). Malformed values are skipped, not rejected.
     */
    static Map<String, String> parseLinks(List<String> headers) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String header : headers) {
            Matcher m = LINK_VALUE.matcher(header);
            while (m.find()) {
                String target = m.group(1).trim();
                String params = m.group(2) == null ? "" : m.group(2);
                Matcher r = REL_PARAM.matcher(params);
                while (r.find()) {
                    String rel = r.group(1) != null ? r.group(1) : r.group(2);
                    for (String one : rel.trim().split("\\s+")) {
                        if (!one.isEmpty()) {
                            out.putIfAbsent(one.toLowerCase(java.util.Locale.ROOT), target);
                        }
                    }
                }
            }
        }
        return out;
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
