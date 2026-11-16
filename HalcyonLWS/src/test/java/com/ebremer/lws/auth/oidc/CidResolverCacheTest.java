package com.ebremer.lws.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link CidResolver} reuses the documents it dereferenced, for as long as their {@code Cache-Control}
 * allows, and tells a failure that says nothing about the credential (unreachable, {@code 429},
 * {@code 5xx}) from one that does. Without the cache every request that presents an ID token cost its
 * identity provider one fetch, and lws-authn, which limits the rate per address, answered {@code 429}
 * in the middle of a Touchstone run.
 */
class CidResolverCacheTest {

    /** A clock the test moves. */
    private static final class TestClock extends Clock {
        private volatile Instant now = Instant.parse("2026-11-10T12:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final Set<String> LOOPBACK = Set.of("127.0.0.1");

    private final TestClock clock = new TestClock();
    private final Map<String, AtomicInteger> fetches = new ConcurrentHashMap<>();
    private HttpServer server;
    private CidResolver resolver;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            fetches.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
            int status = switch (path) {
                case "/busy" -> 429;
                case "/down" -> 503;
                case "/gone" -> 404;
                default -> 200;
            };
            String cacheControl = switch (path) {
                case "/short" -> "public, max-age=30";
                case "/never" -> "no-store";
                default -> null;
            };
            byte[] body = ("<" + url(path) + "> <http://example.org/p> \"x\" .\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/turtle");
            if (cacheControl != null) {
                exchange.getResponseHeaders().set("Cache-Control", cacheControl);
            }
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        resolver = new CidResolver(HttpClient.newHttpClient(), Duration.ofSeconds(5), clock);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private int fetched(String path) {
        return fetches.getOrDefault(path, new AtomicInteger()).get();
    }

    @Test
    void aDocumentIsReusedUntilItsTimeIsUp() {
        resolver.dereference(url("/doc"), LOOPBACK);
        resolver.dereference(url("/doc"), LOOPBACK);
        assertEquals(1, fetched("/doc"), "the second dereference came from the cache");
        clock.advance(CidResolver.DEFAULT_TTL.plusSeconds(1));
        resolver.dereference(url("/doc"), LOOPBACK);
        assertEquals(2, fetched("/doc"), "fetched again once the default time had passed");
    }

    @Test
    void maxAgeIsHonoured() {
        resolver.dereference(url("/short"), LOOPBACK);
        clock.advance(Duration.ofSeconds(29));
        resolver.dereference(url("/short"), LOOPBACK);
        assertEquals(1, fetched("/short"));
        clock.advance(Duration.ofSeconds(2));
        resolver.dereference(url("/short"), LOOPBACK);
        assertEquals(2, fetched("/short"), "max-age=30 is over after 31 seconds");
    }

    @Test
    void noStoreIsNeverCached() {
        resolver.dereference(url("/never"), LOOPBACK);
        resolver.dereference(url("/never"), LOOPBACK);
        assertEquals(2, fetched("/never"));
    }

    @Test
    void aCallerGetsItsOwnCopy() {
        Model first = resolver.dereference(url("/doc"), LOOPBACK);
        first.removeAll();
        Model second = resolver.dereference(url("/doc"), LOOPBACK);
        assertFalse(second.isEmpty(), "emptying one caller's model leaves the cached document whole");
        assertEquals(1, fetched("/doc"));
    }

    @Test
    void theGuardStillRunsForACachedDocument() {
        resolver.dereference(url("/doc"), LOOPBACK);
        assertThrows(SsrfGuard.BlockedException.class, () -> resolver.dereference(url("/doc"), Set.of()));
    }

    @Test
    void busyAndUnavailableAreTemporaryAndNotFoundIsNot() {
        assertTrue(assertThrows(CidResolver.CidException.class,
                () -> resolver.dereference(url("/busy"), LOOPBACK)).temporary(), "429");
        assertTrue(assertThrows(CidResolver.CidException.class,
                () -> resolver.dereference(url("/down"), LOOPBACK)).temporary(), "503");
        assertFalse(assertThrows(CidResolver.CidException.class,
                () -> resolver.dereference(url("/gone"), LOOPBACK)).temporary(), "404");
        assertTrue(assertThrows(CidResolver.CidException.class,
                () -> resolver.dereference("http://127.0.0.1:9/cid", LOOPBACK)).temporary(), "unreachable");
        resolver.dereference(url("/doc"), LOOPBACK);
        assertEquals(0, fetched("/busy") - 1, "a failure is not cached: one fetch each");
    }

    @Test
    void cacheControlIsReadAsRfc9111Says() {
        assertEquals(CidResolver.DEFAULT_TTL, CidResolver.ttl(null));
        assertEquals(CidResolver.DEFAULT_TTL, CidResolver.ttl("public"));
        assertEquals(Duration.ofSeconds(30), CidResolver.ttl("public, max-age=30"));
        assertEquals(CidResolver.MAX_TTL, CidResolver.ttl("max-age=86400"), "capped");
        assertEquals(Duration.ZERO, CidResolver.ttl("max-age=0"));
        assertEquals(Duration.ZERO, CidResolver.ttl("no-cache"));
        assertEquals(Duration.ZERO, CidResolver.ttl("private, no-store"));
    }
}
