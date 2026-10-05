package com.ebremer.lws.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.ebremer.lws.acp.AcrStore;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.http.LwsServlet;
import com.ebremer.lws.oauth.AuthorizationServerMetadataServlet;
import com.ebremer.lws.oauth.AuthorizationServerSettings;
import com.ebremer.lws.oauth.JwksServlet;
import com.ebremer.lws.oauth.LwsAuthorizationServer;
import com.ebremer.lws.oauth.TokenEndpointServlet;
import com.ebremer.lws.store.LwsStore;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The LWS 1.0 test suite, run against a live storage.
 *
 * <p>Since w3c/lws-protocol#213 (25 September 2026) the protocol's README links a core test suite,
 * <a href="https://github.com/lws-contrib/lws-test-suite">lws-contrib/lws-test-suite</a>
 * ({@code lws10/manifest.yaml} @ {@code b8cb134}). It is a declarative manifest with no runner, so
 * this class is the runner: one test per manifest entry, named after it, issuing the entry's request
 * against a real {@link LwsServlet} and the embedded authorization server over a real socket, wired
 * as the Halcyon app wires them.
 *
 * <p>Every entry is {@code mf:Proposed}, and several predate the drafts they cite. Where an entry's
 * expected response contradicts the current normative text, the assertion here is the normative
 * text's, and the test says which entry field is stale and which change made it so. The suite is
 * a map of what to exercise; the drafts decide what is right. {@code docs/lws/conformance.md} lists
 * the stale entries.
 *
 * <p>Two entries are not here. {@code authz-token-exchange-valid} needs a live OpenID provider to
 * mint the subject token; the exchange itself is pinned by {@code TokenExchangeTest} and
 * {@code AuthorizationServerServletTest}, with a stand-in suite. And the suite's
 * {@code auth/*} manifests are empty.
 *
 * <p>The storage speaks as {@code https://storage.example}, the suite's own host, while Jetty
 * listens on an ephemeral loopback port: every URI this storage mints comes from its
 * configuration, never from the request's {@code Host}, which is exactly what makes that possible.
 * It runs with {@code :LWSAcceptAuthenticationCredentials false}, so the only credential a storage
 * accepts is an access token from the authorization server — lws10-core's baseline, alone.
 */
class LwsTestSuiteConformanceTest {

    private static final String SITE = "https://storage.example";
    /** The storage URI, which here is also the storage root container. */
    private static final String STORAGE = SITE + "/alice/";
    private static final String ALICE = "https://id.example/alice";
    private static final String CLIENT = "https://app.example/id";

    private static final String LWS = "https://www.w3.org/ns/lws#";
    private static final String REL_STORAGE = LWS + "storage";
    private static final String LWS_JSON = "application/lws+json";
    private static final String LWS_CID = "application/lws+cid";
    private static final String LINKSET_JSON = "application/linkset+json";

    private static final String SHOPPING_LIST = """
            milk
            eggs
            bread
            butter
            apples
            orange juice
            """;

    private static Server server;
    private static HttpClient http;
    private static String origin;
    private static LwsStorageConfig cfg;
    private static LwsAuthorizationServer as;

    @BeforeAll
    static void start() throws Exception {
        Path cwd = Path.of("").toAbsolutePath();
        // settings.ttl is read from the working directory, and this writes one. Refuse to run
        // anywhere but the directory the lws-conformance execution gives it, so a real
        // deployment's settings can never be overwritten by running this from an IDE.
        Assumptions.assumeTrue(cwd.getFileName().toString().equals("lws-conformance"),
                "run by the lws-conformance surefire execution, in its own working directory");

        deleteTree(cwd.resolve("lws-tdb2"));
        deleteTree(cwd.resolve("content"));
        Files.createDirectories(cwd.resolve("content"));
        Files.writeString(cwd.resolve("settings.ttl"), """
                PREFIX :    <https://halcyon.is/ns/>
                PREFIX lws: <https://www.w3.org/ns/lws#>
                <http://localhost> a :HalcyonSettingsFile ;
                    :ProxyHostName "%s" ;
                    :LWSStoreLocation "lws-tdb2" ;
                    :LWSOwner <%s> ;
                    :LWSAcceptAuthenticationCredentials false ;
                    :hasLWSStorage [ a lws:Storage ; :urlPath "/alice" ;
                                     :storageRoot <%s> ; :namingPolicy "slug" ] .
                """.formatted(SITE, ALICE, cwd.resolve("content").toUri()));

        cfg = LwsSettings.get().storages().getFirst();
        as = LwsAuthorizationServer.init(LwsStore.get());
        assertNotNull(as, "the embedded authorization server should be running");

        server = new Server(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        ServletContextHandler ctx = new ServletContextHandler("/");
        ServletHolder storage = new ServletHolder(new LwsServlet(cfg));
        storage.setInitOrder(3);
        ctx.addServlet(storage, cfg.servletMapping());
        ctx.addServlet(new ServletHolder(new AuthorizationServerMetadataServlet(as.settings(),
                as.exchange().subjectTokenTypes())), AuthorizationServerSettings.METADATA_PATH);
        ctx.addServlet(new ServletHolder(new TokenEndpointServlet(as.exchange())),
                AuthorizationServerSettings.TOKEN_PATH);
        ctx.addServlet(new ServletHolder(new JwksServlet(as.keys())),
                AuthorizationServerSettings.JWKS_PATH);
        server.setHandler(ctx);
        server.start();
        origin = "http://127.0.0.1:" + ((ServerConnector) server.getConnectors()[0]).getLocalPort();
        http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @AfterAll
    static void stop() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    // --- Discovery ----------------------------------------------------------

    /**
     * {@code discovery-unauthorized-response-headers}.
     *
     * <p>Stale entry field: the suite expects {@code Link rel="storageDescription"} pointing at a
     * separate description URI. #183 made the storage description the storage URI itself, and the
     * relation lws10-core now requires on a 401 (SHOULD) and on every GET/HEAD (MUST) is
     * {@code https://www.w3.org/ns/lws#storage}.
     */
    @Test
    void discoveryUnauthorizedResponseHeaders() throws Exception {
        String path = "/alice/metadata/";
        givenContainer(path, false);

        Res r = send("GET", path, null, Map.of(), null);

        assertEquals(401, r.status());
        assertConformingChallenge(r, path, null);
        assertEquals(STORAGE, r.link(REL_STORAGE).orElseThrow().target());
    }

    /**
     * {@code discovery-storage-description}.
     *
     * <p>Stale entry: the suite fetches {@code /alice/description} as {@code application/lws+json}
     * and expects a {@code StorageDescription} and an {@code AuthorizationServer} service. Since
     * #183 a request for the <em>storage URI</em> returns the description, a controlled identifier
     * document served as {@code application/lws+cid}, whose {@code @context} starts with the CID and
     * LWS contexts and whose {@code service} MUST include a {@code StorageRoot}. The authorization
     * server is discovered from the 401 challenge, not the description.
     */
    @Test
    void discoveryStorageDescription() throws Exception {
        Res r = send("GET", "/alice/", null, Map.of("Accept", LWS_CID), null);

        assertEquals(200, r.status());
        assertEquals(LWS_CID, r.mediaType());
        JsonObject d = r.json();
        JsonArray context = d.getJsonArray("@context");
        assertEquals("https://www.w3.org/ns/cid/v1", context.getString(0));
        assertEquals("https://www.w3.org/ns/lws/v1", context.getString(1));
        assertEquals(STORAGE, d.getString("id"));
        assertTrue(hasType(d, "Storage"), "type must be or contain Storage: " + d.get("type"));
        JsonObject root = d.getJsonArray("service").stream()
                .map(JsonValue::asJsonObject)
                .filter(s -> hasType(s, "StorageRoot"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no StorageRoot service in " + d));
        assertEquals(STORAGE, root.getString("serviceEndpoint"));
        for (JsonValue c : d.getJsonArray("capability")) {
            assertTrue(c.asJsonObject().containsKey("type"), "every capability has a type: " + c);
        }
        assertEquals(STORAGE, r.link(REL_STORAGE).orElseThrow().target());
    }

    /**
     * Not a suite entry: Touchstone's {@code discovery-storage-description-default-media-type}.
     * "Requests for the storage URI MUST return ... a media type of {@code application/lws+cid},
     * unless content negotiation requires a different format": with no {@code Accept}, or only a
     * wildcard, the storage URI answers with the description, to anyone. Naming a container format
     * is what reads the root container instead.
     */
    @Test
    void theStorageUriAnswersWithTheDescriptionUnlessAContainerFormatIsNamed() throws Exception {
        for (Map<String, String> h : List.of(Map.<String, String>of(), Map.of("Accept", "*/*"))) {
            Res r = send("GET", "/alice/", null, h, null);
            assertEquals(200, r.status(), h + ": " + r.text());
            assertEquals(LWS_CID, r.mediaType(), h.toString());
            assertEquals(STORAGE, r.json().getString("id"));
        }

        Res listing = send("GET", "/alice/", owner(), Map.of("Accept", LWS_JSON), null);
        assertEquals(200, listing.status());
        assertEquals(LWS_JSON, listing.mediaType());
        assertTrue(hasType(listing.json(), "Container"), "type Container: " + listing.json());
    }

    /**
     * {@code discovery-get-links-storageDescription}. Stale relation, as in
     * {@link #discoveryUnauthorizedResponseHeaders}: {@code lws#storage}, to the storage URI.
     */
    @Test
    void discoveryGetLinksStorage() throws Exception {
        givenContainer("/alice/notes/", true);

        Res r = send("GET", "/alice/notes/", null, Map.of(), null);

        assertEquals(200, r.status());
        assertEquals(STORAGE, r.link(REL_STORAGE).orElseThrow().target());
    }

    // --- Containers ---------------------------------------------------------

    /**
     * {@code getContainer}.
     *
     * <p>The suite's body fixture has no {@code totalItems}, which lws10-core's container
     * representation makes REQUIRED; that is asserted here.
     */
    @Test
    void getContainer() throws Exception {
        givenContainer("/alice/notes/", true);

        Res r = send("GET", "/alice/notes/", null, Map.of("Accept", LWS_JSON), null);

        assertEquals(200, r.status());
        assertEquals(LWS_JSON, r.mediaType());
        assertContainerLinks(r, SITE + "/alice/notes/", STORAGE);
        assertEquals(STORAGE, r.link(REL_STORAGE).orElseThrow().target());
        JsonObject c = r.json();
        assertTrue(contextIncludes(c, "https://www.w3.org/ns/lws/v1"), "the LWS context: " + c);
        assertEquals(SITE + "/alice/notes/", c.getString("id"));
        assertTrue(hasType(c, "Container"), "type Container: " + c.get("type"));
        assertTrue(c.get("totalItems") instanceof JsonNumber, "totalItems is REQUIRED: " + c);
        assertEquals(JsonValue.ValueType.ARRAY, c.get("items").getValueType());
    }

    /** {@code getContainer-private-unauthorized}. */
    @Test
    void getContainerPrivateUnauthorized() throws Exception {
        givenContainer("/alice/private/", false);

        Res r = send("GET", "/alice/private/", null, Map.of(), null);

        assertEquals(401, r.status());
        // Nothing was presented, so RFC 6750 §3.1: the challenge carries no error code.
        assertConformingChallenge(r, "/alice/private/", null);
    }

    /** {@code getContainer-authenticated-owner}. */
    @Test
    void getContainerAuthenticatedOwner() throws Exception {
        givenContainer("/alice/private/", false);

        Res r = send("GET", "/alice/private/", owner(), Map.of("Accept", LWS_JSON), null);

        assertEquals(200, r.status());
        assertEquals(LWS_JSON, r.mediaType());
        assertContainerLinks(r, SITE + "/alice/private/", STORAGE);
        assertEquals(SITE + "/alice/private/", r.json().getString("id"));
    }

    /**
     * {@code getContainer-containmentIntegrity}.
     *
     * <p>Stale entry field: the suite's fixture describes a member's media type as
     * {@code contentType}; #219 renamed it {@code format}, which is MUST for a data resource.
     */
    @Test
    void getContainerContainmentIntegrity() throws Exception {
        givenContainer("/alice/notes/", true);
        givenDataResource("/alice/notes/shoppinglist.txt", SHOPPING_LIST, true);

        Res r = send("GET", "/alice/notes/", null, Map.of("Accept", LWS_JSON), null);

        assertEquals(200, r.status());
        JsonObject item = r.json().getJsonArray("items").stream()
                .map(JsonValue::asJsonObject)
                .filter(i -> (SITE + "/alice/notes/shoppinglist.txt").equals(i.getString("id", null)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("shoppinglist.txt is not among the items"));
        assertTrue(hasType(item, "DataResource"), "type DataResource: " + item);
        assertEquals("text/plain", item.getString("format").split(";")[0].trim());
        assertEquals(SHOPPING_LIST.getBytes(StandardCharsets.UTF_8).length,
                item.getJsonNumber("size").intValue());
        Instant.parse(item.getString("modified"));
    }

    /**
     * {@code createContainer}.
     *
     * <p>The suite sends {@code Content-Type: application/lws+json} with an empty body; lws10-core's
     * own example sends no body at all. Both must create.
     */
    @Test
    void createContainer() throws Exception {
        givenAbsent("/alice/notes/");

        Res r = send("POST", "/alice/", owner(), Map.of(
                "Slug", "notes",
                "Link", "<" + LWS + "Container>; rel=\"type\"",
                "Content-Type", LWS_JSON), new byte[0]);

        assertEquals(201, r.status(), r.text());
        assertEquals(SITE + "/alice/notes/", r.location());
        assertContainerLinks(r, SITE + "/alice/notes/", STORAGE);
    }

    // --- Data resources -----------------------------------------------------

    /** {@code createDataResource}. */
    @Test
    void createDataResource() throws Exception {
        givenContainer("/alice/notes/", true);
        givenAbsent("/alice/notes/shoppinglist.txt");

        Res r = send("POST", "/alice/notes/", owner(), Map.of(
                "Slug", "shoppinglist.txt",
                "Content-Type", "text/plain"), SHOPPING_LIST.getBytes(StandardCharsets.UTF_8));

        assertEquals(201, r.status(), r.text());
        assertEquals(SITE + "/alice/notes/shoppinglist.txt", r.location());
        Link linkset = r.link("linkset").orElseThrow();
        assertEquals(SITE + "/alice/notes/shoppinglist.txt.meta", linkset.target());
        assertEquals(LINKSET_JSON, linkset.param("type"));
        assertEquals(SITE + "/alice/notes/", r.link("up").orElseThrow().target());
        assertTrue(r.links("type").stream().anyMatch(l -> (LWS + "DataResource").equals(l.target())));
    }

    /** Touchstone {@code conditional-get-if-unmodified-since-412}, and the same date on a write. */
    @Test
    void ifUnmodifiedSinceIsAPreconditionOnReadsAndWrites() throws Exception {
        givenContainer("/alice/notes/", true);
        Res created = send("POST", "/alice/notes/", owner(), Map.of("Content-Type", "text/plain"),
                SHOPPING_LIST.getBytes(StandardCharsets.UTF_8));
        assertEquals(201, created.status(), created.text());
        String uri = created.location().substring(SITE.length());
        String lastModified = send("GET", uri, owner(), Map.of(), null).header("Last-Modified").orElseThrow();
        String epoch = "Thu, 01 Jan 1970 00:00:00 GMT";

        assertEquals(200, send("GET", uri, owner(), Map.of("If-Unmodified-Since", lastModified), null).status());
        assertEquals(412, send("GET", uri, owner(), Map.of("If-Unmodified-Since", epoch), null).status());
        assertEquals(412, send("GET", "/alice/notes/", owner(), Map.of("If-Unmodified-Since", epoch), null)
                .status());
        assertEquals(412, send("DELETE", uri, owner(), Map.of("If-Unmodified-Since", epoch), null).status());
        assertEquals(200, send("GET", uri, owner(), Map.of(), null).status(), "the refused DELETE removed nothing");
        assertEquals(204, send("DELETE", uri, owner(), Map.of("If-Unmodified-Since", lastModified), null)
                .status());
    }

    /**
     * lws10-core 9.2: a POST's Link headers provide the new resource's initial user-managed
     * metadata, which the Type Search then reads like a PATCHed link (Touchstone
     * type-search-relation-from-link-header). Server-managed relations are skipped, not honoured.
     */
    @Test
    void aPostsLinkHeadersBecomeItsLinkset() throws Exception {
        givenContainer("/alice/notes/", true);
        Res created = send("POST", "/alice/notes/", owner(), Map.of("Content-Type", "text/plain",
                "Link", "<https://shapes.example/S>; rel=\"describedby\", "
                        + "<https://elsewhere.example/acl>; rel=\"acl\", "
                        + "<https://elsewhere.example/>; rel=\"up\""),
                SHOPPING_LIST.getBytes(StandardCharsets.UTF_8));
        assertEquals(201, created.status(), created.text());
        String meta = created.link("linkset").orElseThrow().target().substring(SITE.length());
        JsonObject ctx = send("GET", meta, owner(), Map.of("Accept", LINKSET_JSON), null).json()
                .getJsonArray("linkset").getJsonObject(0);
        assertEquals("https://shapes.example/S",
                ctx.getJsonArray("describedby").getJsonObject(0).getString("href"));
        assertFalse(ctx.toString().contains("elsewhere.example"),
                "acl and up are server-managed: " + ctx);
    }

    /** {@code createDataResource-unauthorized}. */
    @Test
    void createDataResourceUnauthorized() throws Exception {
        givenContainer("/alice/notes/", true);

        Res r = send("POST", "/alice/notes/", null, Map.of("Content-Type", "text/plain"),
                "some content".getBytes(StandardCharsets.UTF_8));

        assertEquals(401, r.status());
        assertConformingChallenge(r, "/alice/notes/", null);
    }

    /** {@code readDataResource}. */
    @Test
    void readDataResource() throws Exception {
        givenContainer("/alice/notes/", true);
        givenDataResource("/alice/notes/shoppinglist.txt", SHOPPING_LIST, true);

        Res r = send("GET", "/alice/notes/shoppinglist.txt", null, Map.of(), null);

        assertEquals(200, r.status());
        assertEquals("text/plain", r.mediaType());
        assertEquals(SHOPPING_LIST, r.text());
        assertTrue(r.header("ETag").isPresent(), "ETag is REQUIRED on GET");
        Link linkset = r.link("linkset").orElseThrow();
        assertEquals(SITE + "/alice/notes/shoppinglist.txt.meta", linkset.target());
        assertEquals(LINKSET_JSON, linkset.param("type"));
        assertEquals(SITE + "/alice/notes/", r.link("up").orElseThrow().target());
        assertTrue(r.links("type").stream().anyMatch(l -> (LWS + "DataResource").equals(l.target())));
        assertEquals(STORAGE, r.link(REL_STORAGE).orElseThrow().target());
    }

    /**
     * {@code updateDataResource}: an <em>unconditional</em> PUT, exactly as the suite sends it.
     *
     * <p>#228 took the 428 out of lws10-core: clients "SHOULD use conditional requests", and a
     * server rejects only a precondition that was sent and failed. So an update without
     * {@code If-Match} must succeed. {@code 200} and {@code 204} are both success for an update.
     */
    @Test
    void updateDataResource() throws Exception {
        givenContainer("/alice/notes/", true);
        givenDataResource("/alice/notes/shoppinglist.txt", SHOPPING_LIST, true);
        String updated = "milk\neggs\nbread\n";

        Res r = send("PUT", "/alice/notes/shoppinglist.txt", owner(),
                Map.of("Content-Type", "text/plain"), updated.getBytes(StandardCharsets.UTF_8));

        assertTrue(r.status() == 200 || r.status() == 204, r.status() + " " + r.text());
        assertEquals(updated, send("GET", "/alice/notes/shoppinglist.txt", null, Map.of(), null)
                .text());
    }

    /** Not a suite entry: the other half of #228. A precondition that is sent and fails is 412. */
    @Test
    void updateWithAStaleIfMatchIsPreconditionFailed() throws Exception {
        givenContainer("/alice/notes/", true);
        givenDataResource("/alice/notes/shoppinglist.txt", SHOPPING_LIST, true);

        Res r = send("PUT", "/alice/notes/shoppinglist.txt", owner(), Map.of(
                "Content-Type", "text/plain",
                "If-Match", "\"not-the-current-tag\""), "x".getBytes(StandardCharsets.UTF_8));

        assertEquals(412, r.status());
        assertEquals(SHOPPING_LIST,
                send("GET", "/alice/notes/shoppinglist.txt", null, Map.of(), null).text());
    }

    /**
     * Not a suite entry: "create it, but never overwrite" — {@code If-None-Match: *} on a resource
     * that exists is a precondition that fails (RFC 9110 §13.1.2), so 412, and nothing is written.
     */
    @Test
    void createOnlyIfAbsentOnAnExistingResourceIsPreconditionFailed() throws Exception {
        givenContainer("/alice/notes/", true);
        givenDataResource("/alice/notes/shoppinglist.txt", SHOPPING_LIST, true);

        Res r = send("PUT", "/alice/notes/shoppinglist.txt", owner(), Map.of(
                "Content-Type", "text/plain",
                "If-None-Match", "*"), "x".getBytes(StandardCharsets.UTF_8));

        assertEquals(412, r.status());
        assertEquals(SHOPPING_LIST,
                send("GET", "/alice/notes/shoppinglist.txt", null, Map.of(), null).text());
    }

    /**
     * {@code deleteDataResource}.
     *
     * <p>Stale entry field: the suite expects {@code 200}. lws10-core: "On success, the server MUST
     * respond with 204 No Content."
     */
    @Test
    void deleteDataResource() throws Exception {
        givenContainer("/alice/notes/", true);
        givenDataResource("/alice/notes/shoppinglist.txt", SHOPPING_LIST, true);

        Res r = send("DELETE", "/alice/notes/shoppinglist.txt", owner(), Map.of(), null);

        assertEquals(204, r.status());
        assertEquals(404, send("GET", "/alice/notes/shoppinglist.txt", owner(), Map.of(), null)
                .status());
        assertFalse(send("GET", "/alice/notes/", owner(), Map.of("Accept", LWS_JSON), null).json()
                .getJsonArray("items").stream()
                .anyMatch(i -> (SITE + "/alice/notes/shoppinglist.txt")
                        .equals(i.asJsonObject().getString("id", null))),
                "a deleted resource leaves its container's items");
    }

    /** {@code deleteDataResource-unauthorized}. */
    @Test
    void deleteDataResourceUnauthorized() throws Exception {
        givenContainer("/alice/notes/", true);
        givenDataResource("/alice/notes/shoppinglist.txt", SHOPPING_LIST, true);

        Res r = send("DELETE", "/alice/notes/shoppinglist.txt", null, Map.of(), null);

        assertEquals(401, r.status());
        assertConformingChallenge(r, "/alice/notes/shoppinglist.txt", null);
        assertEquals(200, send("GET", "/alice/notes/shoppinglist.txt", null, Map.of(), null)
                .status());
    }

    // --- Metadata -----------------------------------------------------------

    /**
     * {@code getLinkset}.
     *
     * <p>The suite's fixture also lists a {@code lws#storageDescription} relation and a
     * {@code linkset} self-link inside the document; neither is required of a linkset by
     * lws10-core, which asks for the storage relation in the {@code Link} header, where the other
     * tests check it.
     */
    @Test
    void getLinkset() throws Exception {
        givenContainer("/alice/notes/", true);

        Res r = send("GET", "/alice/notes/.meta", null, Map.of("Accept", LINKSET_JSON), null);

        assertEquals(200, r.status());
        assertEquals(LINKSET_JSON, r.mediaType());
        assertTrue(r.header("ETag").isPresent(), "a linkset's GET carries an ETag (MUST)");
        JsonObject ctx = r.json().getJsonArray("linkset").getJsonObject(0);
        assertEquals(SITE + "/alice/notes/", ctx.getString("anchor"));
        assertEquals(STORAGE, ctx.getJsonArray("up").getJsonObject(0).getString("href"));
        assertTrue(ctx.getJsonArray("type").stream()
                .anyMatch(t -> (LWS + "Container").equals(t.asJsonObject().getString("href"))));

        // "Servers MUST advertise support for GET and PATCH ... via the Allow header" and
        // JSON Patch "via the Accept-Patch header" (w3c/lws-protocol#255), on GET as on OPTIONS.
        assertTrue(r.header("Accept-Patch").orElse("").contains("application/json-patch+json"),
                "Accept-Patch on GET: " + r.header("Accept-Patch"));
        Res options = send("OPTIONS", "/alice/notes/.meta", null, Map.of(), null);
        String allow = options.header("Allow").orElse("");
        assertTrue(allow.contains("GET") && allow.contains("PATCH"), "Allow: " + allow);
        assertTrue(options.header("Accept-Patch").orElse("")
                .contains("application/json-patch+json"), "Accept-Patch");
    }

    /**
     * Not a suite entry: #228 again, for linksets. The "MUST reject with 428" on an unconditional
     * linkset PUT/PATCH is gone; conditional requests there are a SHOULD.
     */
    @Test
    void anUnconditionalLinksetPatchSucceeds() throws Exception {
        givenContainer("/alice/notes/", true);

        Res r = send("PATCH", "/alice/notes/.meta", owner(),
                Map.of("Content-Type", JSON_PATCH),
                LICENSE_PATCH.getBytes(StandardCharsets.UTF_8));

        assertTrue(r.status() == 200 || r.status() == 204, r.status() + " " + r.text());
    }

    /**
     * Not a suite entry: Touchstone's {@code access-grant-endpoint-is-container}. The access grant
     * and access request endpoints are LWS containers, so their listings carry
     * {@code Link: <lws#Container>; rel="type"} like any other container.
     */
    @Test
    void theAccessEndpointsAreTypedAsContainers() throws Exception {
        for (String path : List.of("/alice/.access/grants", "/alice/.access/requests",
                "/alice/.notifications/subscriptions")) {
            Res r = send("GET", path, owner(), Map.of("Accept", LWS_JSON), null);
            assertEquals(200, r.status(), path + ": " + r.text());
            assertTrue(r.links("type").stream().anyMatch(l -> (LWS + "Container").equals(l.target())),
                    path + " Link: " + r.headers().allValues("Link"));
        }
    }

    /**
     * Not a suite entry: what Touchstone's {@code linkset-patch-merge} sent before the 5 October
     * draft made JSON Patch the required format. JSON Merge Patch is still accepted, and a merge
     * patch is defined over the target's representation, so the RFC 9264 document a GET returns
     * is a patch the linkset understands: here the whole document read back, plus a license.
     */
    @Test
    void aLinksetPatchInTheDocumentFormItWasReadInSucceeds() throws Exception {
        givenDataResource("/alice/doc.txt", "text", false);
        Res read = send("GET", "/alice/doc.txt.meta", owner(), Map.of("Accept", LINKSET_JSON), null);
        JsonObject entry = read.json().getJsonArray("linkset").getJsonObject(0);
        String patch = "{\"linkset\":[" + jakarta.json.Json.createObjectBuilder(entry)
                .add("license", jakarta.json.Json.createArrayBuilder().add(jakarta.json.Json
                        .createObjectBuilder().add("href", "https://creativecommons.org/licenses/by/4.0/")))
                .build() + "]}";

        Res r = send("PATCH", "/alice/doc.txt.meta", owner(), Map.of(
                "Content-Type", "application/merge-patch+json",
                "If-Match", read.header("ETag").orElseThrow()), patch.getBytes(StandardCharsets.UTF_8));

        assertEquals(204, r.status(), r.text());
        JsonObject after = send("GET", "/alice/doc.txt.meta", owner(), Map.of("Accept", LINKSET_JSON),
                null).json().getJsonArray("linkset").getJsonObject(0);
        assertEquals("https://creativecommons.org/licenses/by/4.0/",
                after.getJsonArray("license").getJsonObject(0).getString("href"));
        assertEquals(entry.get("up"), after.get("up"), "server-managed links are untouched");
    }

    /**
     * Not a suite entry: Touchstone's {@code linkset-conditional-412}. The precondition is
     * evaluated before the patch body (RFC 9110 13.2.2), so a stale validator is a 412 even when
     * the body would be refused.
     */
    @Test
    void aStaleLinksetPatchIsAPreconditionFailureBeforeAnythingElse() throws Exception {
        givenDataResource("/alice/stale.txt", "text", false);

        Res r = send("PATCH", "/alice/stale.txt.meta", owner(), Map.of(
                "Content-Type", "application/merge-patch+json",
                "If-Match", "\"touchstone-stale-etag\""),
                "{\"type\":[{\"href\":\"https://example.org/not-a-type\"}]}"
                        .getBytes(StandardCharsets.UTF_8));

        assertEquals(412, r.status(), r.text());

        Res json = send("PATCH", "/alice/stale.txt.meta", owner(), Map.of(
                "Content-Type", JSON_PATCH,
                "If-Match", "\"touchstone-stale-etag\""),
                LICENSE_PATCH.getBytes(StandardCharsets.UTF_8));

        assertEquals(412, json.status(), json.text());
    }

    /** A JSON Patch that adds the license link Touchstone's linkset tests add. */
    private static final String LICENSE_PATCH = "[{\"op\":\"add\",\"path\":\"/linkset/0/license\","
            + "\"value\":[{\"href\":\"https://creativecommons.org/licenses/by/4.0/\"}]}]";
    private static final String JSON_PATCH = "application/json-patch+json";

    /**
     * Not a suite entry: Touchstone's {@code linkset-patch-json-patch}. JSON Patch is the format a
     * linkset MUST take (w3c/lws-protocol#255); its pointers address the document a GET returns,
     * whose first link context object is the resource's own. Adding a license leaves every other
     * link alone, and removing it again takes it away.
     */
    @Test
    void aJsonPatchAddsAndRemovesALinksetLink() throws Exception {
        givenDataResource("/alice/licensed.txt", "text", false);
        Res read = send("GET", "/alice/licensed.txt.meta", owner(), Map.of("Accept", LINKSET_JSON), null);
        JsonObject before = read.json().getJsonArray("linkset").getJsonObject(0);

        Res r = send("PATCH", "/alice/licensed.txt.meta", owner(), Map.of(
                "Content-Type", JSON_PATCH, "If-Match", read.header("ETag").orElseThrow()),
                LICENSE_PATCH.getBytes(StandardCharsets.UTF_8));

        assertEquals(204, r.status(), r.text());
        JsonObject after = send("GET", "/alice/licensed.txt.meta", owner(), Map.of("Accept", LINKSET_JSON),
                null).json().getJsonArray("linkset").getJsonObject(0);
        assertEquals("https://creativecommons.org/licenses/by/4.0/",
                after.getJsonArray("license").getJsonObject(0).getString("href"));
        assertEquals(before.get("up"), after.get("up"), "server-managed links are untouched");
        assertEquals(before.get("type"), after.get("type"), "server-managed links are untouched");

        Res removed = send("PATCH", "/alice/licensed.txt.meta", owner(), Map.of("Content-Type", JSON_PATCH),
                "[{\"op\":\"remove\",\"path\":\"/linkset/0/license\"}]".getBytes(StandardCharsets.UTF_8));

        assertEquals(204, removed.status(), removed.text());
        assertFalse(send("GET", "/alice/licensed.txt.meta", owner(), Map.of("Accept", LINKSET_JSON), null)
                .json().getJsonArray("linkset").getJsonObject(0).containsKey("license"));
    }

    /**
     * Not a suite entry: Touchstone's {@code linkset-up-not-redirected}. A JSON Patch may not
     * change a server-managed relation: setting {@code up} is refused, and the resource's
     * {@code rel="up"} still names its container.
     */
    @Test
    void aJsonPatchCannotRedirectUp() throws Exception {
        givenDataResource("/alice/stays.txt", "text", false);

        Res r = send("PATCH", "/alice/stays.txt.meta", owner(), Map.of("Content-Type", JSON_PATCH),
                ("[{\"op\":\"add\",\"path\":\"/linkset/0/up\","
                        + "\"value\":[{\"href\":\"https://linkset.invalid/forged-parent/\"}]}]")
                        .getBytes(StandardCharsets.UTF_8));

        assertEquals(403, r.status(), r.text());
        Res head = send("HEAD", "/alice/stays.txt", owner(), Map.of(), null);
        assertEquals(List.of(STORAGE), head.links("up").stream().map(l -> l.target()).toList());
    }

    /**
     * Not a suite entry: Touchstone's {@code linkset-patch-stays-linkset}. A patch whose result is
     * not a linkset document is refused, and the linkset stays one.
     */
    @Test
    void aJsonPatchThatWouldBreakTheLinksetIsUnprocessable() throws Exception {
        givenDataResource("/alice/whole.txt", "text", false);

        Res r = send("PATCH", "/alice/whole.txt.meta", owner(), Map.of("Content-Type", JSON_PATCH),
                "[{\"op\":\"replace\",\"path\":\"/linkset\",\"value\":\"not a linkset\"}]"
                        .getBytes(StandardCharsets.UTF_8));

        assertEquals(422, r.status(), r.text());
        assertTrue(send("GET", "/alice/whole.txt.meta", owner(), Map.of("Accept", LINKSET_JSON), null)
                .json().get("linkset") instanceof jakarta.json.JsonArray);
    }

    /**
     * A JSON Patch applies whole or not at all (RFC 6902 section 5): a failed {@code test} after an
     * {@code add} is a 409, and the linkset and its entity tag are as they were.
     */
    @Test
    void aJsonPatchWhoseTestFailsChangesNothing() throws Exception {
        givenDataResource("/alice/atomic.txt", "text", false);
        String etag = send("GET", "/alice/atomic.txt.meta", owner(), Map.of("Accept", LINKSET_JSON), null)
                .header("ETag").orElseThrow();

        Res r = send("PATCH", "/alice/atomic.txt.meta", owner(), Map.of("Content-Type", JSON_PATCH),
                ("[{\"op\":\"add\",\"path\":\"/linkset/0/license\",\"value\":[{\"href\":\"https://example.org/l\"}]},"
                        + "{\"op\":\"test\",\"path\":\"/linkset/0/anchor\",\"value\":\"https://example.org/other\"}]")
                        .getBytes(StandardCharsets.UTF_8));

        assertEquals(409, r.status(), r.text());
        Res after = send("GET", "/alice/atomic.txt.meta", owner(), Map.of("Accept", LINKSET_JSON), null);
        assertEquals(etag, after.header("ETag").orElseThrow());
        assertFalse(after.json().getJsonArray("linkset").getJsonObject(0).containsKey("license"));
    }

    /** A JSON Patch document that is not one is a 400, and a format the linkset does not take a 415. */
    @Test
    void aMalformedOrUnknownLinksetPatchIsRefused() throws Exception {
        givenDataResource("/alice/malformed.txt", "text", false);

        Res noOp = send("PATCH", "/alice/malformed.txt.meta", owner(), Map.of("Content-Type", JSON_PATCH),
                "[{\"path\":\"/linkset/0/license\"}]".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, noOp.status(), noOp.text());

        Res noValue = send("PATCH", "/alice/malformed.txt.meta", owner(), Map.of("Content-Type", JSON_PATCH),
                "[{\"op\":\"add\",\"path\":\"/linkset/0/license\"}]".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, noValue.status(), noValue.text());

        Res sparql = send("PATCH", "/alice/malformed.txt.meta", owner(),
                Map.of("Content-Type", "application/sparql-update"),
                "INSERT DATA {}".getBytes(StandardCharsets.UTF_8));
        assertEquals(415, sparql.status(), sparql.text());
        assertTrue(sparql.header("Accept-Patch").orElse("").startsWith(JSON_PATCH),
                "Accept-Patch: " + sparql.header("Accept-Patch"));
    }

    /** A document-form patch speaks for this resource only. */
    @Test
    void aLinksetPatchForAnotherAnchorIsUnprocessable() throws Exception {
        givenDataResource("/alice/anchor.txt", "text", false);

        Res r = send("PATCH", "/alice/anchor.txt.meta", owner(),
                Map.of("Content-Type", "application/merge-patch+json"),
                ("{\"linkset\":[{\"anchor\":\"" + SITE + "/alice/other.txt\","
                        + "\"license\":[{\"href\":\"https://example.org/x\"}]}]}")
                        .getBytes(StandardCharsets.UTF_8));

        assertEquals(422, r.status(), r.text());
    }

    /**
     * Not a suite entry: Touchstone's {@code delete-empty-container}, which failed whenever another
     * test wrote an access policy between its GET and its conditional DELETE. A policy change
     * somewhere else does not change this container's listing, so it must not change its tag.
     */
    @Test
    void aPolicyChangeElsewhereLeavesAContainersTagAlone() throws Exception {
        givenContainer("/alice/tagged/", false);
        String etag = send("GET", "/alice/tagged/", owner(), Map.of("Accept", LWS_JSON), null)
                .header("ETag").orElseThrow();

        givenContainer("/alice/elsewhere/", true);   // writes an ACR on another resource

        Res r = send("DELETE", "/alice/tagged/", owner(), Map.of("If-Match", etag), null);
        assertEquals(204, r.status(), r.text());
    }

    /**
     * The other half: a listing is filtered per agent, so an agent whose view of a container
     * changes -- here the public, when a member becomes readable -- gets a new tag, while the
     * owner, whose view did not change, keeps the one it had.
     */
    @Test
    void aFilteredListingsTagFollowsTheAgentsView() throws Exception {
        givenAbsent("/alice/view/");
        givenContainer("/alice/view/", true);
        givenDataResource("/alice/view/open.txt", "open", true);
        givenDataResource("/alice/view/closed.txt", "closed", false);
        String publicBefore = send("GET", "/alice/view/", null, Map.of("Accept", LWS_JSON), null)
                .header("ETag").orElseThrow();
        String ownerBefore = send("GET", "/alice/view/", owner(), Map.of("Accept", LWS_JSON), null)
                .header("ETag").orElseThrow();

        givenAccess("/alice/view/closed.txt", true);

        Res pub = send("GET", "/alice/view/", null, Map.of("Accept", LWS_JSON), null);
        assertEquals(2, pub.json().getInt("totalItems"), pub.text());
        assertNotEquals(publicBefore, pub.header("ETag").orElseThrow(),
                "the public's listing changed, so its tag must");
        assertEquals(ownerBefore, send("GET", "/alice/view/", owner(), Map.of("Accept", LWS_JSON),
                null).header("ETag").orElseThrow(), "the owner's listing did not");
    }

    // --- Authorization ------------------------------------------------------

    /**
     * {@code authz-server-metadata-well-known}. The metadata sits at {@code as_uri} +
     * {@code /.well-known/lws-configuration}, and its {@code issuer} is that same {@code as_uri}.
     */
    @Test
    void authzServerMetadataWellKnown() throws Exception {
        Res r = send("GET", AuthorizationServerSettings.METADATA_PATH, null,
                Map.of("Accept", "application/json"), null);

        assertEquals(200, r.status());
        assertEquals("application/json", r.mediaType());
        JsonObject m = r.json();
        assertEquals(SITE, m.getString("issuer"));
        assertEquals(as.settings().tokenEndpointUri(), m.getString("token_endpoint"));
        assertEquals(as.settings().jwksUri(), m.getString("jwks_uri"));
        assertTrue(strings(m.getJsonArray("grant_types_supported"))
                .contains("urn:ietf:params:oauth:grant-type:token-exchange"));
        // RFC 8414 §2: REQUIRED.
        assertTrue(m.containsKey("response_types_supported"));
        // The CID suite is always configured: its token type, and the DID methods it resolves.
        assertEquals(List.of("https", "did:key", "did:web"),
                strings(m.getJsonArray("subject_identifier_types_supported")));
        assertTrue(strings(m.getJsonArray("subject_token_types_supported"))
                .contains("urn:ietf:params:oauth:token-type:jwt"));
    }

    /**
     * {@code authz-token-exchange-invalid-resource}.
     *
     * <p>The suite expects {@code "error": "invalid_request"}, copying lws10-core's generic example
     * of an error response. For a {@code resource} that names no storage, RFC 8693 §2.2.2 is
     * specific: "the {@code invalid_target} error code SHOULD be used". lws10-core requires only
     * that the request be rejected with an RFC 6749 §5.2 error, which {@code invalid_target} is.
     */
    @Test
    void authzTokenExchangeInvalidResource() throws Exception {
        String form = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange"
                + "&resource=https%3A%2F%2Funknown.example%2F"
                + "&subject_token_type=urn%3Aietf%3Aparams%3Aoauth%3Atoken-type%3Aid_token"
                + "&subject_token=eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.e30.c2ln";

        Res r = send("POST", AuthorizationServerSettings.TOKEN_PATH, null,
                Map.of("Content-Type", "application/x-www-form-urlencoded"),
                form.getBytes(StandardCharsets.UTF_8));

        assertEquals(400, r.status());
        assertEquals("application/json", r.mediaType());
        assertEquals("invalid_target", r.json().getString("error"));
    }

    /** {@code authz-expired-token-rejected}. */
    @Test
    void authzExpiredTokenRejected() throws Exception {
        givenContainer("/alice/private/", false);
        Instant past = Instant.now().minusSeconds(3600);
        String expired = accessToken(ALICE, past, past.plusSeconds(300));

        Res r = send("GET", "/alice/private/", expired, Map.of(), null);

        assertEquals(401, r.status());
        assertConformingChallenge(r, "/alice/private/", "invalid_token");
    }

    /**
     * Not a suite entry, but what the suite's authenticated entries presume: a token for one
     * storage is not good at another. Here, a token whose {@code aud} is some other storage.
     */
    @Test
    void aTokenForAnotherStorageIsRejected() throws Exception {
        givenContainer("/alice/private/", false);
        Map<String, Object> claims = claims(ALICE);
        claims.put("aud", SITE + "/bob");
        Instant now = Instant.now();
        String elsewhere = as.keys().sign(claims, Date.from(now), Date.from(now.plusSeconds(300)));

        Res r = send("GET", "/alice/private/", elsewhere, Map.of(), null);

        assertEquals(401, r.status());
        assertConformingChallenge(r, "/alice/private/", "invalid_token");
    }

    // --- Assertions ---------------------------------------------------------

    /**
     * lws10-core §Authorization Server Discovery: a 401 carries a {@code Bearer} challenge with
     * {@code as_uri} (the {@code iss} of a valid token) and {@code realm}, and the request URI must
     * be logically contained in the realm — which the client is told to verify, so it is verified
     * here too.
     */
    private static void assertConformingChallenge(Res r, String path, String error) {
        String challenge = r.header("WWW-Authenticate")
                .orElseThrow(() -> new AssertionError("401 without WWW-Authenticate"));
        assertTrue(challenge.regionMatches(true, 0, "Bearer ", 0, 7), challenge);
        Map<String, String> p = authParams(challenge.substring(7));
        assertEquals(SITE, p.get("as_uri"), challenge);
        assertEquals(as.settings().issuer(), p.get("as_uri"));
        String realm = p.get("realm");
        assertNotNull(realm, challenge);
        String realmPrefix = realm.endsWith("/") ? realm : realm + "/";
        assertTrue((SITE + path).startsWith(realmPrefix),
                "the request URI must be logically contained within the realm: " + challenge);
        assertEquals(error, p.get("error"), challenge);
    }

    /** lws10-core §read: a container's GET carries linkset, up, and type=Container. */
    private static void assertContainerLinks(Res r, String uri, String parent) {
        Link linkset = r.link("linkset").orElseThrow(() -> new AssertionError("no linkset link"));
        assertEquals(uri + ".meta", linkset.target());
        assertEquals(LINKSET_JSON, linkset.param("type"));
        assertEquals(parent, r.link("up").orElseThrow(() -> new AssertionError("no up")).target());
        assertTrue(r.links("type").stream().anyMatch(l -> (LWS + "Container").equals(l.target())),
                "type=Container: " + r.headers().allValues("Link"));
        if (r.status() == 200) {
            assertTrue(r.header("ETag").isPresent(), "ETag is REQUIRED on GET");
        }
    }

    // --- Prerequisites ------------------------------------------------------

    /**
     * The suite's {@code prereqs.hierarchy}: the container exists, and is readable by anyone
     * ({@code Role-Public}) or only by the owner ({@code Role-Owner}). Write is the owner's either
     * way — the bootstrap policy on the storage root.
     */
    private static void givenContainer(String path, boolean publicRead) throws Exception {
        if (send("HEAD", path, owner(), Map.of(), null).status() == 404) {
            String parent = path.substring(0, path.lastIndexOf('/', path.length() - 2) + 1);
            String slug = path.substring(parent.length(), path.length() - 1);
            Res created = send("POST", parent, owner(), Map.of(
                    "Slug", slug,
                    "Link", "<" + LWS + "Container>; rel=\"type\""), new byte[0]);
            assertEquals(201, created.status(), "prerequisite " + path + ": " + created.text());
        }
        givenAccess(path, publicRead);
    }

    private static void givenDataResource(String path, String body, boolean publicRead)
            throws Exception {
        givenAbsent(path);
        String parent = path.substring(0, path.lastIndexOf('/') + 1);
        Res created = send("POST", parent, owner(), Map.of(
                "Slug", path.substring(parent.length()),
                "Content-Type", "text/plain"), body.getBytes(StandardCharsets.UTF_8));
        assertEquals(201, created.status(), "prerequisite " + path + ": " + created.text());
        givenAccess(path, publicRead);
    }

    private static void givenAbsent(String path) throws Exception {
        int s = send("DELETE", path, owner(), Map.of("Depth", "infinity"), null).status();
        assertTrue(s == 204 || s == 404, "prerequisite: " + path + " absent, got " + s);
    }

    /** Replace the resource's ACR: a public-read policy, or none (inheriting the owner's). */
    private static void givenAccess(String path, boolean publicRead) throws Exception {
        String uri = SITE + path;
        String acr = AcrStore.acrUri(uri);
        String ttl = """
                @prefix acp: <http://www.w3.org/ns/solid/acp#> .
                @prefix acl: <http://www.w3.org/ns/auth/acl#> .
                <%1$s> acp:resource <%2$s> .
                """.formatted(acr, uri);
        if (publicRead) {
            ttl += """
                    <%1$s> acp:accessControl <%1$s#public> .
                    <%1$s#public> a acp:AccessControl ; acp:apply <%1$s#read> .
                    <%1$s#read> a acp:Policy ; acp:allOf <%1$s#anyone> ; acp:allow acl:Read .
                    <%1$s#anyone> a acp:Matcher ; acp:agent acp:PublicAgent .
                    """.formatted(acr);
        }
        String acrPath = path + ".acr";
        String etag = send("GET", acrPath, owner(), Map.of("Accept", "text/turtle"), null)
                .header("ETag").orElse(null);
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "text/turtle");
        if (etag != null) {
            h.put("If-Match", etag);
        }
        Res r = send("PUT", acrPath, owner(), h, ttl.getBytes(StandardCharsets.UTF_8));
        assertEquals(204, r.status(), "prerequisite: access on " + path + ": " + r.text());
    }

    // --- Credentials --------------------------------------------------------

    /** An access token for the storage owner, as the token endpoint would mint it. */
    private static String owner() {
        Instant now = Instant.now();
        return accessToken(ALICE, now, now.plusSeconds(300));
    }

    private static String accessToken(String sub, Instant iat, Instant exp) {
        return as.keys().sign(claims(sub), Date.from(iat), Date.from(exp));
    }

    /** Every claim lws10-core makes REQUIRED of an access token, bar the two times. */
    private static Map<String, Object> claims(String sub) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", as.settings().issuer());
        claims.put("sub", sub);
        claims.put("client_id", CLIENT);
        claims.put("aud", cfg.realm());
        claims.put("jti", UUID.randomUUID().toString());
        return claims;
    }

    // --- HTTP ---------------------------------------------------------------

    private static Res send(String method, String path, String bearer, Map<String, String> headers,
            byte[] body) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(origin + path))
                .timeout(Duration.ofSeconds(30))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(body));
        if (bearer != null) {
            b.header("Authorization", "Bearer " + bearer);
        }
        headers.forEach(b::header);
        HttpResponse<byte[]> r = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        return new Res(r.statusCode(), r.headers(), r.body(), SITE + path);
    }

    record Link(String target, String rel, Map<String, String> params) {
        String param(String name) {
            return params.get(name);
        }
    }

    record Res(int status, HttpHeaders headers, byte[] body, String uri) {

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        JsonObject json() {
            try (var reader = Json.createReader(new StringReader(text()))) {
                return reader.readObject();
            }
        }

        Optional<String> header(String name) {
            return headers.firstValue(name);
        }

        String mediaType() {
            return header("Content-Type").map(v -> v.split(";")[0].trim().toLowerCase(Locale.ROOT))
                    .orElse(null);
        }

        String location() {
            return URI.create(uri).resolve(header("Location")
                    .orElseThrow(() -> new AssertionError("no Location"))).toString();
        }

        List<Link> links(String rel) {
            return parseLinks(headers.allValues("Link"), uri).stream()
                    .filter(l -> l.rel().equals(rel))
                    .toList();
        }

        Optional<Link> link(String rel) {
            List<Link> all = links(rel);
            if (all.size() > 1) {
                fail("more than one rel=\"" + rel + "\" link: " + all);
            }
            return all.stream().findFirst();
        }
    }

    private static final Pattern LINK_VALUE =
            Pattern.compile("<([^>]*)>((?:\\s*;\\s*[^;,=\\s]+\\s*=\\s*(?:\"[^\"]*\"|[^;,\\s]*))*)");
    private static final Pattern LINK_PARAM =
            Pattern.compile(";\\s*([^;,=\\s]+)\\s*=\\s*(?:\"([^\"]*)\"|([^;,\\s]*))");

    /** RFC 8288 Link values, one entry per relation type, targets resolved against the request. */
    private static List<Link> parseLinks(List<String> values, String base) {
        List<Link> out = new ArrayList<>();
        for (String value : values) {
            Matcher m = LINK_VALUE.matcher(value);
            while (m.find()) {
                String target = URI.create(base).resolve(m.group(1).trim()).toString();
                Map<String, String> params = new LinkedHashMap<>();
                Matcher p = LINK_PARAM.matcher(m.group(2));
                while (p.find()) {
                    params.put(p.group(1).toLowerCase(Locale.ROOT),
                            p.group(2) != null ? p.group(2) : p.group(3));
                }
                String rels = params.getOrDefault("rel", "");
                for (String rel : rels.trim().split("\\s+")) {
                    if (!rel.isEmpty()) {
                        out.add(new Link(target, rel, params));
                    }
                }
            }
        }
        return out;
    }

    /** The auth-params of an RFC 9110 challenge, names lower-cased. */
    private static Map<String, String> authParams(String params) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile("([A-Za-z0-9_\\-]+)\\s*=\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([^,\\s]*))")
                .matcher(params);
        while (m.find()) {
            out.put(m.group(1).toLowerCase(Locale.ROOT), m.group(2) != null ? m.group(2) : m.group(3));
        }
        return out;
    }

    private static boolean hasType(JsonObject o, String type) {
        JsonValue t = o.get("type");
        if (t instanceof JsonString s) {
            return s.getString().equals(type);
        }
        return t instanceof JsonArray a && strings(a).contains(type);
    }

    private static boolean contextIncludes(JsonObject o, String context) {
        JsonValue c = o.get("@context");
        if (c instanceof JsonString s) {
            return s.getString().equals(context);
        }
        return c instanceof JsonArray a && a.stream()
                .anyMatch(v -> v instanceof JsonString s && s.getString().equals(context));
    }

    private static List<String> strings(JsonArray a) {
        return a.stream().filter(v -> v instanceof JsonString)
                .map(v -> ((JsonString) v).getString()).toList();
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
