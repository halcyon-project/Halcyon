package com.ebremer.lws.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.http.LwsServlet;
import com.ebremer.lws.oauth.LwsAuthorizationServer;
import com.ebremer.lws.store.LwsStore;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import java.util.concurrent.ConcurrentLinkedQueue;
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
 * Moving resources between containers over HTTP, as Touchstone's {@code core/moving} tests ask it
 * (lws10-core, Moving Resources, w3c/lws-protocol#237): a JSON Patch of the linkset that replaces
 * {@code /linkset/0/up/0/href}, conditional on the linkset's ETag.
 *
 * <p>Four storages, served by real {@link LwsServlet}s over a real socket and primed from a
 * {@code settings.ttl} this class writes into its own working directory, as the app primes them:
 * {@code /flat} (uuid naming, the sharded store: a move keeps the URI), {@code /tree} (slug naming,
 * the disk mirror, its watcher running), {@code /fixed} (moving off) and {@code /dataonly} (data
 * resources move, containers do not).
 */
class MoveServletTest {

    private static final String SITE = "https://storage.example";
    private static final String ALICE = "https://id.example/alice";
    private static final String BOB = "https://id.example/bob";
    private static final String CLIENT = "https://app.example/id";

    private static final String LWS = "https://www.w3.org/ns/lws#";
    private static final String LWS_JSON = "application/lws+json";
    private static final String LWS_CID = "application/lws+cid";
    private static final String LINKSET_JSON = "application/linkset+json";
    private static final String JSON_PATCH = "application/json-patch+json";
    private static final String MERGE_PATCH = "application/merge-patch+json";

    private static Server server;
    private static HttpClient http;
    private static String origin;
    private static Path work;
    private static Path content;
    private static final Map<String, LwsStorageConfig> STORAGES = new LinkedHashMap<>();
    private static LwsAuthorizationServer as;
    private static final ConcurrentLinkedQueue<JsonObject> INBOX = new ConcurrentLinkedQueue<>();

    @BeforeAll
    static void start() throws Exception {
        Path cwd = Path.of("").toAbsolutePath();
        // settings.ttl is read from the working directory, and this writes one: refuse to run
        // anywhere but the directory the lws-servlet execution gives it.
        Assumptions.assumeTrue(cwd.getFileName().toString().equals("lws-servlet"),
                "run by the lws-servlet surefire execution, in its own working directory");

        // The store and the content in the JVM's temp directory, as every store-backed test here
        // keeps them: TDB2 commits on every write, and the build directory may be on a slow disk.
        work = Files.createTempDirectory("lws-servlet");
        content = work.resolve("content");
        for (String s : List.of("flat", "tree", "fixed", "dataonly")) {
            Files.createDirectories(content.resolve(s));
        }
        Files.writeString(cwd.resolve("settings.ttl"), """
                PREFIX :    <https://halcyon.is/ns/>
                PREFIX lws: <https://www.w3.org/ns/lws#>
                <http://localhost> a :HalcyonSettingsFile ;
                    :ProxyHostName "%1$s" ;
                    :LWSStoreLocation "%7$s" ;
                    :LWSOwner <%2$s> ;
                    :LWSAcceptAuthenticationCredentials false ;
                    :hasLWSStorage [ a lws:Storage ; :urlPath "/flat" ;
                                     :storageRoot <%3$s> ; :namingPolicy "uuid" ] ;
                    :hasLWSStorage [ a lws:Storage ; :urlPath "/tree" ;
                                     :storageRoot <%4$s> ; :namingPolicy "slug" ] ;
                    :hasLWSStorage [ a lws:Storage ; :urlPath "/fixed" ;
                                     :storageRoot <%5$s> ; :namingPolicy "uuid" ;
                                     :LWSMoveAppliesTo "" ] ;
                    :hasLWSStorage [ a lws:Storage ; :urlPath "/dataonly" ;
                                     :storageRoot <%6$s> ; :namingPolicy "uuid" ;
                                     :LWSMoveAppliesTo "DataResource" ] .
                """.formatted(SITE, ALICE, content.resolve("flat").toUri(),
                content.resolve("tree").toUri(), content.resolve("fixed").toUri(),
                content.resolve("dataonly").toUri(),
                work.resolve("tdb2").toString().replace('\\', '/')));
        // The notification inbox below listens on loopback, which the egress guard refuses unless
        // it is allowed by name.
        Files.writeString(cwd.resolve("lws-oidc.json"),
                "{ \"enabled\": false, \"allowedInternalHosts\": [\"127.0.0.1\"] }");

        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            STORAGES.put(cfg.urlPath(), cfg);
        }
        as = LwsAuthorizationServer.init(LwsStore.get());
        assertNotNull(as, "the embedded authorization server should be running");

        server = new Server(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        ServletContextHandler ctx = new ServletContextHandler("/");
        int order = 3;
        for (LwsStorageConfig cfg : STORAGES.values()) {
            ServletHolder h = new ServletHolder(new LwsServlet(cfg));
            h.setInitOrder(order++);
            ctx.addServlet(h, cfg.servletMapping());
        }
        ctx.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                try (var r = Json.createReader(req.getInputStream())) {
                    INBOX.add(r.readObject());
                }
                resp.setStatus(204);
            }
        }), "/inbox/*");
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
        if (server == null) {
            // Never started (another execution's working directory): touch no singleton, or it
            // would prime itself from -- and write -- a settings.ttl wherever this JVM runs.
            return;
        }
        server.stop();
        try {
            LwsStore.get().raw().close();
        } catch (RuntimeException ignore) {
            // best effort
        }
        deleteTree(work);
    }

    // --- Discovery ----------------------------------------------------------

    @Test
    void aStorageThatMovesDescribesAMoveResourceCapability() throws Exception {
        JsonObject flat = capability("/flat", "MoveResource").orElseThrow();
        assertEquals(List.of("DataResource", "Container"), strings(flat.getJsonArray("appliesTo")));
        assertEquals(JsonValue.TRUE, flat.get("preservesIdentifier"));

        assertEquals(List.of("DataResource"),
                strings(capability("/dataonly", "MoveResource").orElseThrow().getJsonArray("appliesTo")));
        assertTrue(capability("/fixed", "MoveResource").isEmpty(),
                "a storage that does not move resources MUST NOT describe the capability");
        assertEquals(JsonValue.FALSE, capability("/tree", "MoveResource").orElseThrow()
                .get("preservesIdentifier"), "the mirror's URIs are paths on disk");
    }

    // --- Moving on a storage that keeps identifiers --------------------------

    @Test
    void aDataResourceMovesByItsUpLinkAndKeepsItsUri() throws Exception {
        String source = container("/flat", root("/flat"));
        String destination = container("/flat", root("/flat"));
        String moved = data("/flat", source, "a resource on the move");
        String sourceTag = send("GET", source, owner("/flat"), Map.of("Accept", LWS_JSON), null)
                .header("ETag").orElseThrow();
        String destinationTag = send("GET", destination, owner("/flat"), Map.of("Accept", LWS_JSON),
                null).header("ETag").orElseThrow();

        String linkset = linksetOf(moved);
        Res read = send("GET", linkset, owner("/flat"), Map.of("Accept", LINKSET_JSON), null);
        JsonObject ctx = read.json().getJsonArray("linkset").getJsonObject(0);
        assertEquals(moved, ctx.getString("anchor"), "the resource's link context comes first");
        assertEquals(source, ctx.getJsonArray("up").getJsonObject(0).getString("href"),
                "and its up link first in it");

        Res r = moveTo(linkset, destination, read.header("ETag").orElseThrow(), "/flat");

        assertEquals(204, r.status(), r.text());
        String newTag = r.header("ETag").orElseThrow();
        assertNotEquals(read.header("ETag").orElseThrow(), newTag);
        assertEquals(destination, r.link("up").orElseThrow());
        assertEquals(newTag, send("GET", linkset, owner("/flat"), Map.of("Accept", LINKSET_JSON),
                null).header("ETag").orElseThrow(), "the ETag a later GET of the linkset returns");

        assertEquals(List.of(moved), items(destination));
        assertEquals(List.of(), items(source));
        Res after = send("GET", moved, owner("/flat"), Map.of(), null);
        assertEquals(200, after.status());
        assertEquals("a resource on the move", after.text());
        assertEquals(destination, after.link("up").orElseThrow());
        assertNotEquals(sourceTag, send("GET", source, owner("/flat"), Map.of("Accept", LWS_JSON),
                null).header("ETag").orElseThrow());
        assertNotEquals(destinationTag, send("GET", destination, owner("/flat"),
                Map.of("Accept", LWS_JSON), null).header("ETag").orElseThrow());
    }

    @Test
    void otherLinkChangesApplyAtomicallyWithTheMove() throws Exception {
        String source = container("/flat", root("/flat"));
        String destination = container("/flat", root("/flat"));
        String moved = data("/flat", source, "licensed");
        String linkset = linksetOf(moved);

        Res r = patch(linkset, JSON_PATCH, """
                [ { "op": "replace", "path": "/linkset/0/up/0/href", "value": "%s" },
                  { "op": "add", "path": "/linkset/0/license",
                    "value": [ { "href": "https://creativecommons.org/licenses/by/4.0/" } ] } ]
                """.formatted(destination), etagOf(linkset, "/flat"), owner("/flat"));
        assertEquals(204, r.status(), r.text());
        JsonObject ctx = linksetDoc(linkset, "/flat");
        assertEquals("https://creativecommons.org/licenses/by/4.0/",
                ctx.getJsonArray("license").getJsonObject(0).getString("href"));
        assertEquals(destination, ctx.getJsonArray("up").getJsonObject(0).getString("href"));

        // A merge patch moves it too.
        Res back = patch(linkset, MERGE_PATCH, "{ \"up\": [ { \"href\": \"" + source + "\" } ] }",
                etagOf(linkset, "/flat"), owner("/flat"));
        assertEquals(204, back.status(), back.text());
        assertEquals(List.of(moved), items(source));
    }

    @Test
    void aContainerMovesWithWhatItHolds() throws Exception {
        String source = container("/flat", root("/flat"));
        String destination = container("/flat", root("/flat"));
        String moved = container("/flat", source);
        String inner = data("/flat", moved, "carried along");

        Res r = moveTo(linksetOf(moved), destination, etagOf(linksetOf(moved), "/flat"), "/flat");
        assertEquals(204, r.status(), r.text());
        assertEquals(List.of(moved), items(destination));
        assertEquals(List.of(inner), items(moved));
        assertEquals(List.of(), items(source));
    }

    @Test
    void aMoveToAnythingButAnExistingContainerIsRefused() throws Exception {
        String home = container("/flat", root("/flat"));
        String moved = data("/flat", home, "a resource that stays put");
        String notContainer = data("/flat", home, "a data resource is no container");
        String linkset = linksetOf(moved);

        for (String to : List.of(root("/flat") + "nowhere-" + UUID.randomUUID(), notContainer,
                root("/fixed"), "https://elsewhere.example/")) {
            Res r = moveTo(linkset, to, etagOf(linkset, "/flat"), "/flat");
            assertEquals(409, r.status(), to + ": " + r.text());
        }
        Res removed = patch(linkset, JSON_PATCH, "[ { \"op\": \"remove\", \"path\": \"/linkset/0/up\" } ]",
                etagOf(linkset, "/flat"), owner("/flat"));
        assertEquals(409, removed.status(), removed.text());
        assertEquals(home, send("GET", moved, owner("/flat"), Map.of(), null).link("up").orElseThrow());
    }

    @Test
    void aContainerCannotMoveIntoItselfOrWhatItHolds() throws Exception {
        String outer = container("/flat", root("/flat"));
        String inner = container("/flat", outer);
        String linkset = linksetOf(outer);

        Res below = moveTo(linkset, inner, etagOf(linkset, "/flat"), "/flat");
        assertEquals(409, below.status());
        assertEquals("application/problem+json", below.mediaType());
        assertEquals("Cannot move a container into one of its own descendants",
                below.json().getString("title"));
        assertEquals(inner + " is a descendant of " + outer, below.json().getString("detail"));
        assertEquals(409, moveTo(linkset, outer, etagOf(linkset, "/flat"), "/flat").status());
        assertTrue(items(root("/flat")).contains(outer));
        assertEquals(List.of(inner), items(outer));
    }

    @Test
    void aStaleLinksetTagMovesNothing() throws Exception {
        String source = container("/flat", root("/flat"));
        String destination = container("/flat", root("/flat"));
        String moved = data("/flat", source, "stale");

        Res r = moveTo(linksetOf(moved), destination, "\"l999\"", "/flat");
        assertEquals(412, r.status(), r.text());
        assertEquals(List.of(moved), items(source));
    }

    @Test
    void aStorageThatDoesNotMoveRefusesAsItAlwaysDid() throws Exception {
        String destination = container("/fixed", root("/fixed"));
        String moved = data("/fixed", root("/fixed"), "a resource that stays put");
        String linkset = linksetOf(moved);

        Res r = patch(linkset, JSON_PATCH, """
                [ { "op": "add", "path": "/linkset/0/up", "value": [ { "href": "%s" } ] } ]
                """.formatted(destination), etagOf(linkset, "/fixed"), owner("/fixed"));
        assertEquals(403, r.status(), r.text());
        assertEquals(root("/fixed"), send("GET", moved, owner("/fixed"), Map.of(), null)
                .link("up").orElseThrow());
        assertEquals(List.of(), items(destination));
    }

    @Test
    void aStorageThatMovesOnlyDataResourcesRefusesToMoveAContainer() throws Exception {
        String destination = container("/dataonly", root("/dataonly"));
        String moved = container("/dataonly", root("/dataonly"));
        String file = data("/dataonly", root("/dataonly"), "this one moves");

        Res r = moveTo(linksetOf(moved), destination, etagOf(linksetOf(moved), "/dataonly"),
                "/dataonly");
        assertEquals(409, r.status(), r.text());
        assertEquals(204, moveTo(linksetOf(file), destination, etagOf(linksetOf(file), "/dataonly"),
                "/dataonly").status());
        assertEquals(List.of(file), items(destination));
    }

    @Test
    void aMoveNeedsDeleteOfTheResourceAndCreateInTheDestination() throws Exception {
        String home = container("/flat", root("/flat"));
        String destination = container("/flat", root("/flat"));
        String moved = data("/flat", home, "bob may delete this, not put it there");
        // Bob may read, modify and delete the resource -- Write on it, Append on the container it
        // is in -- and read the destination.
        givenAcr(moved, BOB, "acl:Read, acl:Write");
        givenAcr(home, BOB, "acl:Read, acl:Append");
        givenAcr(destination, BOB, "acl:Read");
        String linkset = linksetOf(moved);

        Res refused = moveAs(linkset, destination, etagOf(linkset, "/flat"), token("/flat", BOB));
        assertEquals(403, refused.status(), refused.text());
        assertEquals(List.of(), items(destination));

        // Given create in the destination, but not delete from where it is, still no.
        givenAcr(destination, BOB, "acl:Read, acl:Append");
        givenAcr(home, BOB, "acl:Read");
        assertEquals(403, moveAs(linkset, destination, etagOf(linkset, "/flat"),
                token("/flat", BOB)).status());

        // Both, and it moves.
        givenAcr(home, BOB, "acl:Read, acl:Append");
        Res moves = moveAs(linkset, destination, etagOf(linkset, "/flat"), token("/flat", BOB));
        assertEquals(204, moves.status(), moves.text());
        assertEquals(List.of(moved), items(destination));
    }

    @Test
    void aMoveIsAnnouncedAsOneMoveActivity() throws Exception {
        String test = container("/flat", root("/flat"));
        String source = container("/flat", test);
        String destination = container("/flat", test);
        String moved = data("/flat", source, "a resource on the move");

        Res sub = send("POST", root("/flat") + LwsStorageConfig.SUBSCRIPTIONS, owner("/flat"),
                Map.of("Content-Type", LWS_JSON), Json.createObjectBuilder()
                        .add("@context", Json.createArrayBuilder().add("https://www.w3.org/ns/lws/v1"))
                        .add("type", "WebhookSubscription")
                        .add("topic", Json.createArrayBuilder().add(test))
                        .add("inbox", origin + "/inbox/flat")
                        .build().toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(200, sub.status(), sub.text());

        assertEquals(204, moveTo(linksetOf(moved), destination, etagOf(linksetOf(moved), "/flat"),
                "/flat").status());

        JsonObject move = awaitActivity(moved, "Move");
        assertEquals(source, move.getString("origin"));
        assertEquals(destination, move.getString("target"));
        assertEquals(moved, move.getJsonObject("object").getString("id"));
    }

    // --- Moving on the disk mirror, where identifiers change -----------------

    @Test
    void aMovedResourceTakesANewUriAndTheLinksetSaysWhich() throws Exception {
        String source = container("/tree", root("/tree"));
        String destination = container("/tree", root("/tree"));
        String moved = data("/tree", source, "a resource on the move");
        String name = moved.substring(source.length());
        String linkset = linksetOf(moved);
        assertEquals(204, patch(linkset, JSON_PATCH, LICENSE_PATCH, etagOf(linkset, "/tree"),
                owner("/tree")).status());

        Res r = moveTo(linkset, destination, etagOf(linkset, "/tree"), "/tree");

        // "A server that does not preserve the identifier of a moved resource MUST respond with 200
        // OK and a representation of the updated linkset resource whose anchor is the new
        // identifier of the resource, and MUST include a Content-Location header field carrying the
        // new identifier of the linkset resource itself."
        assertEquals(200, r.status(), r.text());
        assertEquals(LINKSET_JSON, r.mediaType());
        String now = destination + name;
        assertEquals(now + ".meta", URI.create(linkset).resolve(r.header("Content-Location")
                .orElseThrow()).toString());
        JsonObject ctx = r.json().getJsonArray("linkset").getJsonObject(0);
        assertEquals(now, ctx.getString("anchor"));
        assertEquals(destination, ctx.getJsonArray("up").getJsonObject(0).getString("href"));
        assertEquals("https://creativecommons.org/licenses/by/4.0/",
                ctx.getJsonArray("license").getJsonObject(0).getString("href"));
        assertEquals(r.header("ETag").orElseThrow(), etagOf(now + ".meta", "/tree"));

        assertEquals(List.of(now), items(destination));
        assertEquals(List.of(), items(source));
        Res after = send("GET", now, owner("/tree"), Map.of(), null);
        assertEquals(200, after.status());
        assertEquals("a resource on the move", after.text());
        assertEquals(destination, after.link("up").orElseThrow());
        assertTrue(Files.isRegularFile(onDisk(now)), "renamed on disk");
        assertFalse(Files.exists(onDisk(moved)));

        // "Such a server SHOULD respond to subsequent requests on the previous identifiers of the
        // resource and of its auxiliary resources with 301 Moved Permanently" -- to an agent that may
        // read it where it is now.
        for (String suffix : List.of("", ".meta", ".acr")) {
            for (String method : List.of("GET", "HEAD")) {
                Res old = send(method, moved + suffix, owner("/tree"), Map.of(), null);
                assertEquals(301, old.status(), method + " " + suffix);
                assertEquals(now + suffix, URI.create(moved).resolve(old.header("Location")
                        .orElseThrow()).toString());
            }
        }
        assertEquals(404, send("GET", moved, token("/tree", BOB), Map.of(), null).status(),
                "bob may not read it where it is now, so he is not told where that is");
        assertEquals(401, send("GET", moved, null, Map.of(), null).status());

        // Something created at the previous URI takes it back.
        Res again = send("POST", source, owner("/tree"), Map.of("Slug", name,
                "Content-Type", "text/plain"), "a newcomer".getBytes(StandardCharsets.UTF_8));
        assertEquals(201, again.status(), again.text());
        assertEquals(moved, again.location());
        assertEquals("a newcomer", send("GET", moved, owner("/tree"), Map.of(), null).text());
    }

    @Test
    void aMovedContainerTakesItsSubtreeAlongAndTheWatcherLeavesItThere() throws Exception {
        String source = container("/tree", root("/tree"));
        String destination = container("/tree", root("/tree"));
        String moved = container("/tree", source);
        String inner = data("/tree", moved, "carried along");
        String innerName = inner.substring(moved.length());
        givenAcr(inner, BOB, "acl:Read");

        Res r = moveTo(linksetOf(moved), destination, etagOf(linksetOf(moved), "/tree"), "/tree");
        assertEquals(200, r.status(), r.text());
        String arrived = r.json().getJsonArray("linkset").getJsonObject(0).getString("anchor");
        assertEquals(List.of(arrived), items(destination));
        assertEquals(List.of(arrived + innerName), items(arrived));
        assertEquals(List.of(), items(source));

        // The rename on disk is an event the watcher sees. Once a file dropped in afterwards is
        // adopted, a reconcile has run since the move; the moved subtree must have survived it as
        // the move left it, its member's access control included.
        Files.writeString(onDisk(destination).resolve("probe"), "probe");
        Instant until = Instant.now().plusSeconds(30);
        while (send("HEAD", destination + "probe", owner("/tree"), Map.of(), null).status() != 200) {
            assertTrue(Instant.now().isBefore(until), "the watcher never reconciled");
            Thread.sleep(250);
        }
        assertEquals(List.of(arrived + innerName), items(arrived));
        assertEquals(200, send("GET", arrived + innerName, token("/tree", BOB), Map.of(), null)
                .status(), "bob's ACR moved with the member it governs");
        Res old = send("GET", inner, owner("/tree"), Map.of(), null);
        assertEquals(301, old.status(), "nothing was adopted again at the old place");
        assertEquals(arrived + innerName, old.header("Location").orElseThrow(),
                "a descendant's previous URI leads to where it is now");
        assertTrue(items(source).isEmpty());
    }

    @Test
    void aTakenNameIsDisambiguatedOnTheMirror() throws Exception {
        String source = container("/tree", root("/tree"));
        String destination = container("/tree", root("/tree"));
        Res first = send("POST", source, owner("/tree"), Map.of("Slug", "same.txt",
                "Content-Type", "text/plain"), "moving".getBytes(StandardCharsets.UTF_8));
        Res taken = send("POST", destination, owner("/tree"), Map.of("Slug", "same.txt",
                "Content-Type", "text/plain"), "staying".getBytes(StandardCharsets.UTF_8));
        assertEquals(201, first.status());
        assertEquals(201, taken.status());

        Res r = moveTo(linksetOf(first.location()), destination,
                etagOf(linksetOf(first.location()), "/tree"), "/tree");
        assertEquals(200, r.status(), r.text());
        assertEquals(destination + "same-1.txt",
                r.json().getJsonArray("linkset").getJsonObject(0).getString("anchor"));
        assertEquals("staying", send("GET", destination + "same.txt", owner("/tree"), Map.of(), null)
                .text());
    }

    @Test
    void aMirrorContainerCannotMoveIntoWhatItHolds() throws Exception {
        String outer = container("/tree", root("/tree"));
        String inner = container("/tree", outer);
        Res r = moveTo(linksetOf(outer), inner, etagOf(linksetOf(outer), "/tree"), "/tree");
        assertEquals(409, r.status(), r.text());
        assertTrue(Files.isDirectory(onDisk(inner)));
    }

    // --- Helpers: the Touchstone-shaped requests ----------------------------

    private static Res moveTo(String linkset, String destination, String etag, String storage)
            throws Exception {
        return moveAs(linkset, destination, etag, owner(storage));
    }

    private static Res moveAs(String linkset, String destination, String etag, String token)
            throws Exception {
        return patch(linkset, JSON_PATCH, """
                [ { "op": "replace", "path": "/linkset/0/up/0/href", "value": "%s" } ]
                """.formatted(destination), etag, token);
    }

    private static Res patch(String linkset, String type, String body, String etag, String token)
            throws Exception {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", type);
        if (etag != null) {
            h.put("If-Match", etag);
        }
        return send("PATCH", linkset, token, h, body.getBytes(StandardCharsets.UTF_8));
    }

    private static String linksetOf(String uri) throws Exception {
        Res r = send("HEAD", uri, owner(storageOf(uri)), Map.of(), null);
        assertEquals(200, r.status(), uri);
        return r.link("linkset").orElseThrow();
    }

    private static String etagOf(String linkset, String storage) throws Exception {
        return send("GET", linkset, owner(storage), Map.of("Accept", LINKSET_JSON), null)
                .header("ETag").orElseThrow();
    }

    private static JsonObject linksetDoc(String linkset, String storage) throws Exception {
        return send("GET", linkset, owner(storage), Map.of("Accept", LINKSET_JSON), null).json()
                .getJsonArray("linkset").getJsonObject(0);
    }

    private static List<String> items(String container) throws Exception {
        Res r = send("GET", container, owner(storageOf(container)), Map.of("Accept", LWS_JSON), null);
        assertEquals(200, r.status(), container + ": " + r.text());
        return r.json().getJsonArray("items").stream()
                .map(v -> v.asJsonObject().getString("id")).toList();
    }

    private static Optional<JsonObject> capability(String storage, String type) throws Exception {
        Res r = send("GET", root(storage), null, Map.of("Accept", LWS_CID), null);
        assertEquals(200, r.status());
        return r.json().getJsonArray("capability").stream().map(JsonValue::asJsonObject)
                .filter(c -> type.equals(c.getString("type", null))).findFirst();
    }

    private static JsonObject awaitActivity(String object, String type) throws Exception {
        Instant until = Instant.now().plusSeconds(20);
        while (Instant.now().isBefore(until)) {
            for (JsonObject n : INBOX) {
                JsonValue a = n.get("activity");
                List<JsonObject> all = a instanceof JsonArray arr
                        ? arr.stream().map(JsonValue::asJsonObject).toList()
                        : List.of(a.asJsonObject());
                for (JsonObject act : all) {
                    if (strings(act.getJsonArray("type")).contains(type)
                            && object.equals(act.getJsonObject("object").getString("id"))) {
                        return act;
                    }
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("no " + type + " of " + object + " arrived: " + INBOX);
    }

    // --- Helpers: fixtures --------------------------------------------------

    private static final String LICENSE_PATCH = """
            [ { "op": "add", "path": "/linkset/0/license",
                "value": [ { "href": "https://creativecommons.org/licenses/by/4.0/" } ] } ]
            """;

    /** Where the mirror storage keeps a resource on disk: its path under the storage. */
    private static Path onDisk(String uri) {
        String rel = uri.substring(root("/tree").length());
        return content.resolve("tree").resolve(rel.endsWith("/") ? rel.substring(0, rel.length() - 1)
                : rel);
    }

    private static String root(String storage) {
        return SITE + storage + "/";
    }

    private static String storageOf(String uri) {
        String path = uri.substring(SITE.length());
        return path.substring(0, path.indexOf('/', 1));
    }

    private static String container(String storage, String parent) throws Exception {
        Res r = send("POST", parent, owner(storage), Map.of(
                "Slug", "c-" + UUID.randomUUID().toString().substring(0, 8),
                "Link", "<" + LWS + "Container>; rel=\"type\""), new byte[0]);
        assertEquals(201, r.status(), r.text());
        return r.location();
    }

    private static String data(String storage, String parent, String body) throws Exception {
        Res r = send("POST", parent, owner(storage), Map.of(
                "Slug", "d-" + UUID.randomUUID().toString().substring(0, 8) + ".txt",
                "Content-Type", "text/plain"), body.getBytes(StandardCharsets.UTF_8));
        assertEquals(201, r.status(), r.text());
        return r.location();
    }

    /** Give {@code agent} {@code modes} on {@code uri} itself, beside the owner's inherited control. */
    private static void givenAcr(String uri, String agent, String modes) throws Exception {
        String acr = uri + LwsStorageConfig.ACR_SUFFIX;
        String ttl = """
                @prefix acp: <http://www.w3.org/ns/solid/acp#> .
                @prefix acl: <http://www.w3.org/ns/auth/acl#> .
                <%1$s> acp:resource <%2$s> ; acp:accessControl <%1$s#ac> .
                <%1$s#ac> a acp:AccessControl ; acp:apply <%1$s#policy> .
                <%1$s#policy> a acp:Policy ; acp:allOf <%1$s#who> ; acp:allow %3$s .
                <%1$s#who> a acp:Matcher ; acp:agent <%4$s> .
                """.formatted(acr, uri, modes, agent);
        String storage = storageOf(uri);
        String etag = send("GET", acr, owner(storage), Map.of("Accept", "text/turtle"), null)
                .header("ETag").orElseThrow();
        Res r = send("PUT", acr, owner(storage), Map.of("Content-Type", "text/turtle",
                "If-Match", etag), ttl.getBytes(StandardCharsets.UTF_8));
        assertEquals(204, r.status(), r.text());
    }

    private static String owner(String storage) {
        return token(storage, ALICE);
    }

    /** An access token for {@code sub} at {@code storage}, as the token endpoint would mint it. */
    private static String token(String storage, String sub) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", as.settings().issuer());
        claims.put("sub", sub);
        claims.put("client_id", CLIENT);
        claims.put("aud", STORAGES.get(storage).realm());
        claims.put("jti", UUID.randomUUID().toString());
        Instant now = Instant.now();
        return as.keys().sign(claims, Date.from(now), Date.from(now.plusSeconds(300)));
    }

    // --- Helpers: HTTP ------------------------------------------------------

    private static Res send(String method, String uri, String bearer, Map<String, String> headers,
            byte[] body) throws IOException, InterruptedException {
        String path = uri.startsWith(SITE) ? uri.substring(SITE.length()) : uri;
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

        /** The target of the one link of relation {@code rel}, resolved against the request. */
        Optional<String> link(String rel) {
            List<String> out = new ArrayList<>();
            for (String value : headers.allValues("Link")) {
                Matcher m = LINK.matcher(value);
                while (m.find()) {
                    if (List.of(m.group(2).trim().split("\\s+")).contains(rel)) {
                        out.add(URI.create(uri).resolve(m.group(1).trim()).toString());
                    }
                }
            }
            assertTrue(out.size() <= 1, "more than one rel=\"" + rel + "\" link: " + out);
            return out.stream().findFirst();
        }
    }

    private static final Pattern LINK =
            Pattern.compile("<([^>]*)>[^,]*?;\\s*rel=\"([^\"]*)\"");

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
