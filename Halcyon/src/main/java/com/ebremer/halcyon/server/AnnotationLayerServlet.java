package com.ebremer.halcyon.server;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.client.LwsClient;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code GET|PUT /annotationlayer?uri=<layer file>} — Zephyr's session-authenticated relay to an
 * annotation layer's JSON file in a W3C LWS storage.
 *
 * <p>A relay for the reason {@code /savestack} and {@code /colorclasses} are: the storages take
 * bearer tokens only, and the browser has none (C5 keeps the token out of the DOM). The viewer
 * used to {@code PUT} and {@code GET} the file at its storage URI with nothing but the session
 * cookie, which the storage answers 401 — so a drawn layer could be neither saved nor reopened.
 * Here the request is made server-side with the signed-in user's own token, so the storage's ACP
 * is the authorization; this endpoint adds only sanity checks.
 *
 * <p>Narrow on purpose, because a cookie-authenticated write is a CSRF surface: the target must
 * be a {@code .json} resource inside a configured storage, a write must carry
 * {@code Content-Type: application/json} (not a form-submittable type, so a cross-origin page
 * cannot send it without a CORS preflight this endpoint never grants) and a foreign
 * {@code Origin} is refused outright.
 *
 * <p>{@code PUT} replaces an existing file (conditionally, on its current entity tag) or creates
 * it: in a path-mirroring storage at exactly {@code uri}, with any missing containers above it;
 * in the flat storage by {@code POST} into {@code ?container=}, where the storage picks the URI.
 * Either way the answer is {@code {"uri": "<where it is>"}}.
 */
public class AnnotationLayerServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(AnnotationLayerServlet.class);

    /** Cap on a layer file. A dense hand-drawn layer is a few MB; this is generous. */
    private static final int MAX_BODY_BYTES = 64 * 1024 * 1024;

    private static final String JSON = "application/json";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HalcyonPrincipal principal = signedIn(request, response);
        if (principal == null) {
            return;
        }
        String uri = layerUri(request, response);
        if (uri == null) {
            return;
        }
        LwsClient.Text t = client(principal).getText(uri, JSON);
        if (!t.ok()) {
            refuse(response, "read", uri, t.status(), null);
            return;
        }
        response.setContentType(JSON);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(t.body() == null ? "" : t.body());
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HalcyonPrincipal principal = signedIn(request, response);
        if (principal == null) {
            return;
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !sameSite(origin)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "cross-origin write refused");
            return;
        }
        String type = request.getContentType();
        if (type == null || !type.toLowerCase(java.util.Locale.ROOT).startsWith(JSON)) {
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "an annotation layer is application/json");
            return;
        }
        String uri = layerUri(request, response);
        if (uri == null) {
            return;
        }
        LwsStorageConfig cfg = storageOf(uri);

        // Read the body under a hard size cap (readNBytes stops at the limit, so a chunked body
        // with an absent or lying Content-Length cannot exhaust memory).
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "annotation layer too large");
            return;
        }
        try (var reader = Json.createReader(new ByteArrayInputStream(body))) {
            reader.read();
        } catch (JsonException | IllegalStateException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "malformed JSON");
            return;
        }

        LwsClient client = client(principal);
        String etag = client.etag(uri);
        LwsClient.Result r;
        String saved = uri;
        if (etag != null) {
            r = client.put(uri, JSON, body, etag);
        } else if (cfg.naming() == NamingPolicyType.SLUG) {
            // The path-mirroring storage creates on PUT, with any missing containers above.
            r = client.put(uri, JSON, body, null);
        } else {
            String container = request.getParameter("container");
            if (container == null || container.isBlank() || storageOf(container) != cfg) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "a new layer in this storage needs the container to create it in");
                return;
            }
            r = client.post(container, uri.substring(uri.lastIndexOf('/') + 1), JSON, body, false);
            if (r.ok() && r.location() != null) {
                saved = r.location();
            }
        }
        if (!r.ok()) {
            refuse(response, "save", uri, r.status(),
                    r.body() == null ? null : r.body().getString("title", null));
            return;
        }
        response.setContentType(JSON);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(Json.createObjectBuilder().add("uri", saved).build().toString());
    }

    /** Whether an {@code Origin} header names this site (its public or its own host name). */
    static boolean sameSite(String origin) {
        String o = LwsClient.origin(origin);
        HalcyonSettings settings = HalcyonSettings.getSettings();
        return o != null && (o.equals(LwsClient.origin(settings.getProxyHostName()))
                || o.equals(LwsClient.origin(settings.getHostName())));
    }

    /** The signed-in caller, or {@code null} after answering 401. */
    private static HalcyonPrincipal signedIn(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HalcyonPrincipal principal = RequestPrincipal.resolve(request, response);
        if (!RequestPrincipal.isSignedIn(principal)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not signed in");
            return null;
        }
        return principal;
    }

    /** The {@code uri} parameter when it names a layer file in a storage, else {@code null} after a 400. */
    private static String layerUri(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String uri = request.getParameter("uri");
        if (uri == null || uri.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'uri' parameter");
            return null;
        }
        if (!isLayerUri(uri)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "annotation layers are .json resources in the W3C LWS storages");
            return null;
        }
        return uri;
    }

    /** A plain {@code .json} resource URI (no query, no fragment) inside a configured storage. */
    static boolean isLayerUri(String uri) {
        return uri != null && uri.endsWith(".json") && uri.indexOf('?') < 0 && uri.indexOf('#') < 0
                && !uri.endsWith("/.json") && storageOf(uri) != null;
    }

    /** The configured storage a URI belongs to, or {@code null}. */
    private static LwsStorageConfig storageOf(String uri) {
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            if (uri.startsWith(cfg.baseUri() + "/")) {
                return cfg;
            }
        }
        return null;
    }

    private static LwsClient client(HalcyonPrincipal principal) {
        return new LwsClient(principal.getToken(), HalcyonSettings.getSettings().getProxyHostName());
    }

    /** Relay a storage refusal: its status where the browser can use it, never a bare 401. */
    private static void refuse(HttpServletResponse response, String what, String uri, int storageStatus,
            String title) throws IOException {
        int status = switch (storageStatus) {
            case 401, 403 -> HttpServletResponse.SC_FORBIDDEN;
            case 412, 428 -> HttpServletResponse.SC_CONFLICT;
            case 0 -> HttpServletResponse.SC_BAD_GATEWAY;
            default -> storageStatus;
        };
        logger.warn("annotation layer {} of {} answered HTTP {} {}", what, uri, storageStatus,
                title == null ? "" : title);
        response.sendError(status, title == null ? "the storage refused the " + what : title);
    }
}
