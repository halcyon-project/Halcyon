package com.ebremer.lws.oauth;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.http.Problem;
import jakarta.json.Json;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * One storage's OAuth 2.0 Protected Resource Metadata (RFC 9728), which every {@code 401} from the
 * storage points to with the {@code resource_metadata} parameter of its challenge. Since the drafts of
 * 19 October 2026 (w3c/lws-protocol#256) lws10-core §Authorization Server Discovery requires that
 * parameter of a conforming challenge — it replaced {@code as_uri} and {@code realm} — and
 * §Protected Resource Metadata requires the document it names to conform to RFC 9728.
 *
 * <p><b>Where.</b> RFC 9728 §3.1 puts the metadata of a resource at the resource's URL with
 * {@value #PATH} inserted between the host and the path, the query kept after it. Every URI this
 * module mints starts with {@code :ProxyHostName}, which may carry a path
 * ({@code https://host/targets/halcyon}) that the fronting proxy strips before a request reaches this
 * server, which sees {@code /lws/x}. The metadata of {@code https://host/targets/halcyon/lws/x} is then
 * {@code https://host/.well-known/oauth-protected-resource/targets/halcyon/lws/x}, outside that path
 * altogether, and the proxy routes it back with the path stripped from behind the well-known prefix —
 * exactly as it routes the authorization server's {@code /.well-known/lws-configuration/targets/halcyon}
 * to {@code /.well-known/lws-configuration}. So this servlet receives
 * {@code /.well-known/oauth-protected-resource/lws/x} and puts {@code :ProxyHostName} back in front.
 * {@link #metadataUrl} and {@link #resourceUri} are the two directions of that mapping and must stay
 * each other's inverse.
 *
 * <p>It is mounted per storage, on {@link #servletMapping} — the storage's own path behind the
 * well-known prefix — and not on the whole of {@code /.well-known/oauth-protected-resource/*}: the MCP
 * endpoint serves its metadata at {@code /.well-known/oauth-protected-resource/mcp}, and a prefix
 * mapping over the whole tree would shadow it.
 *
 * <p><b>What.</b> {@code resource} is the URL the client requested, character for character: RFC 9728
 * §3.3 has a client discard metadata whose {@code resource} is not identical to the URL it derived the
 * metadata URL from, and lws10-core §Discovery Validation by a Client repeats the rule. So both
 * directions work on the raw request URI — still percent-encoded, as the client sent it — and never
 * decode and re-encode. {@code authorization_servers}, which lws10-core makes REQUIRED with the issuer
 * of at least one authorization server, names the one whose tokens this storage accepts, as
 * {@code CredentialChain#authorizationServer()} decides it for the storage's challenges: this
 * deployment's embedded authorization server when it runs one, otherwise the issuer the Keycloak
 * verifier discovered. {@code bearer_methods_supported} says a token is read from the
 * {@code Authorization} header only, never from a form body or the query (RFC 6750 §2.2, §2.3).
 *
 * <p><b>Nothing about what is stored.</b> The document is the same for every path in the storage — an
 * existing resource, an absent one, a private one — apart from {@code resource}, which only repeats
 * the request. No store is consulted, so it is no existence oracle and needs no authorization; and it
 * is served without authentication, whatever {@code Authorization} header the request carries,
 * because a client fetches it precisely when its credential was refused, often resending the refused
 * one. A path outside the storage, for which there is no protected resource to describe, is 404.
 */
public final class ProtectedResourceMetadataServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** RFC 9728 §3: the well-known URI suffix, registered in §8.3. */
    public static final String PATH = "/.well-known/oauth-protected-resource";

    private static final String ALLOW = "OPTIONS, HEAD, GET";

    private final transient LwsStorageConfig cfg;
    private final transient Supplier<String> authorizationServer;

    /**
     * @param cfg                 the storage whose resources this servlet describes
     * @param authorizationServer the issuer whose access tokens the storage accepts — the storage
     *                            servlet's {@code authorizationServer()}, read per request because it
     *                            is known only once that servlet is initialised ({@code null} before)
     */
    public ProtectedResourceMetadataServlet(LwsStorageConfig cfg, Supplier<String> authorizationServer) {
        this.cfg = cfg;
        this.authorizationServer = authorizationServer;
    }

    /**
     * The servlet mapping for a storage's metadata URLs: its path behind {@value #PATH}, as a prefix
     * mapping — which, as for the storage's own {@link LwsStorageConfig#servletMapping()}, also
     * matches the path itself, the metadata URL of the storage root spelled without its slash.
     */
    public static String servletMapping(LwsStorageConfig cfg) {
        return PATH + ("/".equals(cfg.urlPath()) ? "" : cfg.urlPath()) + "/*";
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String method = req.getMethod();
        if ("OPTIONS".equals(method)) {
            resp.setHeader("Allow", ALLOW);
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            Problem.methodNotAllowed("protected resource metadata is read-only", ALLOW).writeTo(resp);
            return;
        }
        // The resource is rebuilt from the raw path; whether it lies in this storage is decided on
        // the decoded one, as the container decided which servlet answers both the resource and this
        // request. A raw URI that does not literally continue the well-known path (its prefix
        // percent-encoded, which the container decodes before mapping) is no URL a challenge of this
        // server points to, and a path outside the storage has no resource here to describe.
        String suffix = suffix(requestPath(req));
        String mapped = suffix(req.getServletPath() + Objects.toString(req.getPathInfo(), ""));
        if (suffix == null || mapped == null || !inStorage(mapped)) {
            Problem.notFound("there is no protected resource of this storage at that path").writeTo(resp);
            return;
        }
        String issuer = authorizationServer.get();
        if (issuer == null) {
            new Problem(503, "Service Unavailable", "the storage is not ready").writeTo(resp);
            return;
        }
        byte[] body = document(resourceUri(cfg.siteUrl(), suffix, req.getQueryString()), issuer);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/json");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // As public as the authorization server's metadata, and for the same reason: it is derived
        // from configuration and the URL alone, so a shared cache may keep it per URL.
        resp.setHeader("Cache-Control", "public, max-age=3600");
        resp.setContentLength(body.length);
        if ("GET".equals(method)) {
            resp.getOutputStream().write(body);
        }
    }

    private static byte[] document(String resource, String issuer) {
        return Json.createObjectBuilder()
                .add("resource", resource)
                .add("authorization_servers", Json.createArrayBuilder().add(issuer))
                .add("bearer_methods_supported", Json.createArrayBuilder().add("header"))
                .build().toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Whether a path after {@value #PATH} names this storage or something inside it. */
    private boolean inStorage(String suffix) {
        String base = cfg.urlPath();
        return "/".equals(base) || suffix.equals(base) || suffix.startsWith(base + "/");
    }

    /**
     * The URL of the protected resource metadata for a request this server received: the origin of
     * {@code siteUrl}, {@value #PATH}, then the path of the URL the client requested —
     * {@code siteUrl}'s path, which the proxy stripped, followed by {@code rawPath} — and the query,
     * if there was one (RFC 9728 §3.1). A requested path of only {@code /} is dropped, as §3.1
     * requires of a terminating slash following the host.
     *
     * @param siteUrl  {@code :ProxyHostName}, with no trailing slash ({@link LwsStorageConfig#siteUrl()})
     * @param rawPath  the request path as this server received it, raw and still percent-encoded
     *                 ({@link #requestPath})
     * @param rawQuery the raw query string, or {@code null} when the request had none
     */
    public static String metadataUrl(String siteUrl, String rawPath, String rawQuery) {
        int slash = pathStart(siteUrl);
        String origin = slash < 0 ? siteUrl : siteUrl.substring(0, slash);
        String sitePath = slash < 0 ? "" : siteUrl.substring(slash);
        String requested = sitePath + (rawPath.startsWith("/") ? rawPath : "/" + rawPath);
        if ("/".equals(requested)) {
            requested = "";
        }
        return origin + PATH + requested + (rawQuery == null ? "" : "?" + rawQuery);
    }

    /**
     * The resource a metadata request describes — the URL whose {@link #metadataUrl} it was — from
     * what follows {@value #PATH} in the request this server received: {@code siteUrl}, that suffix,
     * then the query. An empty suffix under a {@code siteUrl} without a path is the root of the host,
     * whose {@code /} {@link #metadataUrl} dropped.
     *
     * @param suffix   the raw path after {@value #PATH}: empty, or starting with {@code /}
     * @param rawQuery the raw query string, or {@code null}
     */
    public static String resourceUri(String siteUrl, String suffix, String rawQuery) {
        String path = suffix.isEmpty() && pathStart(siteUrl) < 0 ? "/" : suffix;
        return siteUrl + path + (rawQuery == null ? "" : "?" + rawQuery);
    }

    /**
     * The request path as this server received it — the raw request URI, still percent-encoded,
     * without the context path — or {@code /} when that is empty.
     */
    public static String requestPath(HttpServletRequest req) {
        String uri = req.getRequestURI();
        String ctx = req.getContextPath();
        String p = ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
        return p.isEmpty() ? "/" : p;
    }

    /** The path after {@value #PATH}, or {@code null} when the path does not literally continue it. */
    private static String suffix(String rawPath) {
        if (!rawPath.startsWith(PATH)) {
            return null;
        }
        String suffix = rawPath.substring(PATH.length());
        return suffix.isEmpty() || suffix.startsWith("/") ? suffix : null;
    }

    /** Where the path of an absolute URL starts, or {@code -1} when it has none. */
    private static int pathStart(String url) {
        int scheme = url.indexOf("://");
        return url.indexOf('/', scheme < 0 ? 0 : scheme + 3);
    }
}
