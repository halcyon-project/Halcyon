package com.ebremer.halcyon.server;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.client.LwsClient;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * {@code GET /lwsopen?uri=<resource>} — opens a storage resource in the browser, as the signed-in
 * user.
 *
 * <p>The storage browsers link each resource so it can be opened in a tab. Linked straight to its
 * storage URI, that tab sends the session cookie and nothing else, and the storages take bearer
 * tokens only — so every resource that is not public answered
 * {@code 401 authentication is required}, including the user's own. This endpoint makes the
 * request server-side with the session's own token (or anonymously when signed out, which still
 * serves what is public) and relays the bytes; the storage's ACP makes the access decision on
 * every request, exactly as for the previews' relay.
 *
 * <p>What comes back is stored content, served from this site's origin, so it is never allowed
 * to act as this site: every response carries {@code Content-Security-Policy: sandbox} (a unique
 * opaque origin, no script), {@code X-Content-Type-Options: nosniff} and
 * {@code Cross-Origin-Resource-Policy: same-origin} (another site cannot pull a user's resource
 * in through a tag with the user's cookie). {@code GET} only, and only URIs inside a configured
 * storage — no open proxy.
 */
public class LwsOpenServlet extends HttpServlet {

    /** The servlet's path. */
    public static final String PATH = "/lwsopen";

    /** The link that opens {@code uri} through this endpoint. */
    public static String href(String uri) {
        return PATH + "?uri=" + URLEncoder.encode(uri, StandardCharsets.UTF_8);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String uri = request.getParameter("uri");
        if (uri == null || uri.isBlank() || !withinConfiguredStorage(uri)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "not a resource of this site's storages");
            return;
        }
        HalcyonPrincipal principal = RequestPrincipal.resolve(request, response);
        boolean signedIn = RequestPrincipal.isSignedIn(principal);
        LwsClient client = new LwsClient(signedIn ? principal.getToken() : null,
                HalcyonSettings.getSettings().getProxyHostName());

        LwsClient.Stream s = client.stream(uri);
        try (InputStream in = s.body()) {
            if (!s.ok()) {
                if (s.status() == 401) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, signedIn
                            ? "your session's sign-in has expired; sign in again"
                            : "sign in to open this resource");
                } else {
                    response.sendError(s.status() == 0 ? HttpServletResponse.SC_BAD_GATEWAY : s.status(),
                            "the storage answered HTTP " + s.status());
                }
                return;
            }
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(displayType(s.contentType()));
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Security-Policy", "sandbox");
            response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("Content-Disposition", "inline; filename*=UTF-8''" + fileName(uri));
            if (s.length() >= 0) {
                response.setContentLengthLong(s.length());
            }
            OutputStream out = response.getOutputStream();
            in.transferTo(out);
        }
    }

    /**
     * The media type to answer with. Text the browser would otherwise offer to download — Turtle,
     * CSV, N-Triples, ... — is served as {@code text/plain} so it is shown; HTML keeps its type
     * (it renders, sandboxed) and everything else is passed through.
     */
    static String displayType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        String bare = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (bare.startsWith("text/") && !bare.equals("text/html") && !bare.equals("text/xml")) {
            return "text/plain; charset=UTF-8";
        }
        return contentType;
    }

    /** The resource's last path segment, percent-encoded for {@code filename*}. */
    static String fileName(String uri) {
        String path = uri;
        int cut = path.indexOf('?');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        cut = path.indexOf('#');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        try {
            name = URLDecoder.decode(name.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // keep the raw segment
        }
        if (name.isBlank()) {
            name = "resource";
        }
        return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** No open proxy: only resources of a configured storage. */
    private static boolean withinConfiguredStorage(String uri) {
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            if (uri.startsWith(cfg.baseUri() + "/")) {
                return true;
            }
        }
        return false;
    }
}
