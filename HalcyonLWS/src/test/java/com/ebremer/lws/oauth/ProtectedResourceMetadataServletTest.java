package com.ebremer.lws.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * Pins a storage's RFC 9728 protected resource metadata, and the mapping between a requested URL and
 * the URL of its metadata, which lws10-core's challenge now carries as {@code resource_metadata}.
 *
 * <p>The mapping is the part that is easy to get subtly wrong and impossible to notice from one side:
 * the challenge builds a metadata URL from the request it refuses, a proxy strips
 * {@code :ProxyHostName}'s path from behind the well-known prefix, and this servlet rebuilds the
 * resource from what is left. A client then discards the document unless its {@code resource} is the
 * URL it requested, character for character. So the two directions are tested as one round trip,
 * with and without a path in the site URL, through the proxy, for the storage root spelled both
 * ways, a container, a data resource, an encoded name and a query.
 */
class ProtectedResourceMetadataServletTest {

    private static final String ISSUER = "https://halcyon.example";

    private static LwsStorageConfig storage(String urlPath, String siteUrl) {
        return new LwsStorageConfig(urlPath, Path.of("content"), NamingPolicyType.SLUG, siteUrl);
    }

    private static FakeHttp.Response call(ProtectedResourceMetadataServlet servlet,
            HttpServletRequest req) throws IOException {
        FakeHttp.Response out = new FakeHttp.Response();
        servlet.service(req, FakeHttp.response(out));
        return out;
    }

    private static ProtectedResourceMetadataServlet servlet(LwsStorageConfig cfg) {
        return new ProtectedResourceMetadataServlet(cfg, () -> ISSUER);
    }

    /** What a path-stripping proxy in front of {@code siteUrl} hands this server for {@code url}. */
    private static String throughTheProxy(String siteUrl, String url) {
        String origin = siteUrl.replaceFirst("^(https?://[^/]+).*$", "$1");
        String sitePath = siteUrl.substring(origin.length());
        assertTrue(url.startsWith(origin + ProtectedResourceMetadataServlet.PATH + sitePath),
                "the metadata URL is on the site's origin, with its path behind the well-known prefix: "
                        + url);
        String path = url.substring(origin.length());
        int q = path.indexOf('?');
        path = q < 0 ? path : path.substring(0, q);
        String suffix = path.substring(ProtectedResourceMetadataServlet.PATH.length() + sitePath.length());
        return ProtectedResourceMetadataServlet.PATH + suffix;
    }

    // --- The mapping --------------------------------------------------------

    /**
     * Request URL to metadata URL and back, through the proxy: the document's {@code resource} is the
     * URL the client requested, exactly.
     */
    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
        // site URL,                               path the server sees,  raw query
        "https://halcyon.example,                  /lws,                  NULL",
        "https://halcyon.example,                  /lws/,                 NULL",
        "https://halcyon.example,                  /lws/notes/,           NULL",
        "https://halcyon.example,                  /lws/notes/list.txt,   NULL",
        "https://halcyon.example,                  /lws/a%20b%2Fc,        NULL",
        "https://halcyon.example,                  /lws/notes/,           page=2&q=%2F",
        "https://halcyon.example:8443,             /lws/notes/list.txt,   NULL",
        "https://vulcan.example/targets/halcyon,   /lws,                  NULL",
        "https://vulcan.example/targets/halcyon,   /lws/,                 NULL",
        "https://vulcan.example/targets/halcyon,   /lws/notes/list.txt,   NULL",
        "https://vulcan.example/targets/halcyon,   /lws/notes/,           page=2",
    })
    void theMetadataOfARequestDescribesExactlyTheUrlRequested(String site, String path, String query)
            throws IOException {
        String requested = site + path + (query == null ? "" : "?" + query);
        String metadata = ProtectedResourceMetadataServlet.metadataUrl(site, path, query);

        FakeHttp.Response out = call(servlet(storage("/lws", site)),
                FakeHttp.at("GET", throughTheProxy(site, metadata), query));

        assertEquals(200, out.status, out.body());
        assertEquals(requested, out.json().getString("resource"));
    }

    @Test
    void theMetadataUrlInsertsTheWellKnownPathBetweenTheHostAndThePath() {
        // RFC 9728 §3.1, and lws10-core's own example.
        assertEquals("https://storage.example/.well-known/oauth-protected-resource/storage_1/photos/img.jpg",
                ProtectedResourceMetadataServlet.metadataUrl("https://storage.example",
                        "/storage_1/photos/img.jpg", null));
        assertEquals("https://h.example/.well-known/oauth-protected-resource/lws/",
                ProtectedResourceMetadataServlet.metadataUrl("https://h.example", "/lws/", null));
        assertEquals("https://h.example/.well-known/oauth-protected-resource/lws",
                ProtectedResourceMetadataServlet.metadataUrl("https://h.example", "/lws", null));
        // The query is kept, after the path.
        assertEquals("https://h.example/.well-known/oauth-protected-resource/lws/c/?page=2",
                ProtectedResourceMetadataServlet.metadataUrl("https://h.example", "/lws/c/", "page=2"));
        // The site's path, which the proxy strips before this server sees the request, is the
        // first part of the path requested, so it goes behind the well-known prefix too.
        assertEquals("https://vulcan.example/.well-known/oauth-protected-resource/targets/halcyon/lws/x",
                ProtectedResourceMetadataServlet.metadataUrl("https://vulcan.example/targets/halcyon",
                        "/lws/x", null));
        // A terminating slash following the host is dropped (§3.1).
        assertEquals("https://h.example/.well-known/oauth-protected-resource",
                ProtectedResourceMetadataServlet.metadataUrl("https://h.example", "/", null));
    }

    @Test
    void aStorageAtTheRootOfItsHostIsDescribedByTheBareWellKnownUrl() throws IOException {
        // The inverse of the dropped slash: an empty suffix at the root of a host is "/".
        LwsStorageConfig root = storage("/", "https://h.example");
        assertEquals(ProtectedResourceMetadataServlet.PATH + "/*",
                ProtectedResourceMetadataServlet.servletMapping(root));

        FakeHttp.Response out = call(servlet(root),
                FakeHttp.at("GET", ProtectedResourceMetadataServlet.PATH, null));

        assertEquals(200, out.status, out.body());
        assertEquals("https://h.example/", out.json().getString("resource"));
    }

    @Test
    void eachStorageIsMappedUnderItsOwnPathSoOtherMetadataIsNotShadowed() {
        // Not /.well-known/oauth-protected-resource/*: the MCP endpoint's metadata lives at
        // /.well-known/oauth-protected-resource/mcp.
        assertEquals("/.well-known/oauth-protected-resource/lws/*",
                ProtectedResourceMetadataServlet.servletMapping(storage("/lws", ISSUER)));
        assertEquals("/.well-known/oauth-protected-resource/a/b/*",
                ProtectedResourceMetadataServlet.servletMapping(storage("/a/b/", ISSUER)));
    }

    // --- The document -------------------------------------------------------

    @Test
    void theDocumentNamesTheResourceAndTheAuthorizationServer() throws IOException {
        FakeHttp.Response out = call(servlet(storage("/lws", ISSUER)),
                FakeHttp.at("GET", "/.well-known/oauth-protected-resource/lws/notes/", null));

        assertEquals(200, out.status);
        assertEquals("application/json", out.contentType);
        assertEquals("public, max-age=3600", out.headers.get("Cache-Control"));
        JsonObject d = out.json();
        assertEquals(ISSUER + "/lws/notes/", d.getString("resource"));
        assertEquals(List.of(ISSUER), strings(d, "authorization_servers"),
                "lws10-core makes authorization_servers REQUIRED, with at least one issuer");
        assertEquals(List.of("header"), strings(d, "bearer_methods_supported"));
        assertEquals(out.body.size(), out.contentLength.intValue());
    }

    @Test
    void theDocumentIsTheSameForEveryPathButItsResource() throws IOException {
        // No store is consulted: an existing resource, an absent one and a private one cannot be
        // told apart here, so the endpoint is no existence oracle and needs no authorization.
        ProtectedResourceMetadataServlet servlet = servlet(storage("/lws", ISSUER));
        JsonObject a = call(servlet, FakeHttp.at("GET",
                "/.well-known/oauth-protected-resource/lws/exists.txt", null)).json();
        JsonObject b = call(servlet, FakeHttp.at("GET",
                "/.well-known/oauth-protected-resource/lws/never/was.txt", null)).json();

        assertEquals(ISSUER + "/lws/never/was.txt", b.getString("resource"));
        assertEquals(a.getJsonArray("authorization_servers"), b.getJsonArray("authorization_servers"));
        assertEquals(a.getJsonArray("bearer_methods_supported"), b.getJsonArray("bearer_methods_supported"));
        assertEquals(a.keySet(), b.keySet());
    }

    @Test
    void headAnswersAsGetWithoutTheBody() throws IOException {
        FakeHttp.Response get = call(servlet(storage("/lws", ISSUER)),
                FakeHttp.at("GET", "/.well-known/oauth-protected-resource/lws/x", null));
        FakeHttp.Response head = call(servlet(storage("/lws", ISSUER)),
                FakeHttp.at("HEAD", "/.well-known/oauth-protected-resource/lws/x", null));

        assertEquals(200, head.status);
        assertEquals("application/json", head.contentType);
        assertEquals(get.contentLength, head.contentLength);
        assertEquals(0, head.body.size());
    }

    @Test
    void optionsListsTheMethods() throws IOException {
        FakeHttp.Response out = call(servlet(storage("/lws", ISSUER)),
                FakeHttp.at("OPTIONS", "/.well-known/oauth-protected-resource/lws/x", null));

        assertEquals(204, out.status);
        assertEquals("OPTIONS, HEAD, GET", out.headers.get("Allow"));
    }

    @Test
    void theDocumentIsReadOnly() throws IOException {
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            FakeHttp.Response out = call(servlet(storage("/lws", ISSUER)),
                    FakeHttp.at(method, "/.well-known/oauth-protected-resource/lws/x", null));

            assertEquals(405, out.status, method);
            assertEquals("OPTIONS, HEAD, GET", out.headers.get("Allow"), method);
            assertEquals("application/problem+json", out.contentType, method);
        }
    }

    /**
     * Served without looking at the request's credentials: {@link FakeHttp#at} fails the test if
     * the servlet reads any header at all, {@code Authorization} included. A client fetches this
     * document precisely when its token was refused, and often resends that token with it.
     */
    @Test
    void theRequestsCredentialsAreNeverRead() throws IOException {
        FakeHttp.Response out = call(servlet(storage("/lws", ISSUER)),
                FakeHttp.at("GET", "/.well-known/oauth-protected-resource/lws/private/", null));

        assertEquals(200, out.status);
    }

    @Test
    void aPathOutsideTheStorageHasNoMetadataHere() throws IOException {
        ProtectedResourceMetadataServlet servlet = servlet(storage("/lws", ISSUER));
        for (String path : List.of(
                "/.well-known/oauth-protected-resource/other/x",
                "/.well-known/oauth-protected-resource/lwsx",
                "/.well-known/oauth-protected-resource",
                // The well-known prefix itself percent-encoded: the container decodes it before
                // mapping, but it is no URL a challenge of this server points to.
                "/.well-known/oauth-protected-resourc%65/lws/x")) {
            FakeHttp.Response out = call(servlet, FakeHttp.at("GET", path, null));

            assertEquals(404, out.status, path);
            assertEquals("application/problem+json", out.contentType, path);
            assertEquals("no-store", out.headers.get("Cache-Control"), path);
        }
    }

    @Test
    void aPathTheContainerDecodesIntoTheStorageIsDescribedAsRequested() throws IOException {
        // The storage's own servlet answers (and challenges) a request for /%6Cws/x, which the
        // container decodes to /lws/x; its metadata names the URL as it was requested.
        FakeHttp.Response out = call(servlet(storage("/lws", ISSUER)),
                FakeHttp.at("GET", "/.well-known/oauth-protected-resource/%6Cws/x", null));

        assertEquals(200, out.status, out.body());
        assertEquals(ISSUER + "/%6Cws/x", out.json().getString("resource"));
    }

    @Test
    void beforeTheStorageIsReadyThereIsNoIssuerToName() throws IOException {
        FakeHttp.Response out = call(new ProtectedResourceMetadataServlet(storage("/lws", ISSUER), () -> null),
                FakeHttp.at("GET", "/.well-known/oauth-protected-resource/lws/x", null));

        assertEquals(503, out.status);
        assertEquals("no-store", out.headers.get("Cache-Control"), "an error is never cacheable");
    }

    private static List<String> strings(JsonObject o, String name) {
        return o.getJsonArray(name).stream().map(v -> ((JsonString) v).getString()).toList();
    }
}
