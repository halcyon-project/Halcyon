package com.ebremer.lws.auth.oidc;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CidResolver}: the SPARQL trust check on a parsed CID, and the full SSRF-guarded,
 * content-negotiated dereference over a loopback {@link HttpServer} (Turtle and JSON-LD).
 */
class CidResolverTest {

    private static final String SUB = "https://alice.example/#me";
    private static final String ISS = "https://issuer.example/realms/Halcyon";

    private static final String TTL =
            "<" + SUB + "> <https://www.w3.org/ns/did#service> [ "
            + "a <https://www.w3.org/ns/lws#OpenIdProvider> ; "
            + "<https://www.w3.org/ns/did#serviceEndpoint> <" + ISS + "> ] .";

    private static final String JSONLD =
            "{ \"@context\": [\"https://www.w3.org/ns/cid/v1\"], \"id\": \"" + SUB + "\", "
            + "\"service\": [ { \"type\": \"https://www.w3.org/ns/lws#OpenIdProvider\", "
            + "\"serviceEndpoint\": \"" + ISS + "\" } ] }";

    private final CidResolver resolver = new CidResolver();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String serve(String body, String contentType, int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cid", exchange -> {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            if (contentType != null) {
                exchange.getResponseHeaders().set("Content-Type", contentType);
            }
            exchange.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(b);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/cid";
    }

    private static Model turtle(String ttl) {
        Model m = ModelFactory.createDefaultModel();
        RDFDataMgr.read(m, new ByteArrayInputStream(ttl.getBytes(StandardCharsets.UTF_8)), "https://ex/", Lang.TURTLE);
        return m;
    }

    @Test
    void declaresProviderWhenTheCidNamesTheIssuer() {
        assertTrue(resolver.declaresOpenIdProvider(turtle(TTL), SUB, ISS));
    }

    @Test
    void rejectsADifferentIssuer() {
        assertFalse(resolver.declaresOpenIdProvider(turtle(TTL), SUB, "https://evil.example/"));
    }

    @Test
    void rejectsADifferentSubject() {
        assertFalse(resolver.declaresOpenIdProvider(turtle(TTL), "https://mallory.example/#me", ISS));
    }

    @Test
    void dereferencesAndParsesTurtle() throws IOException {
        String url = serve(TTL, "text/turtle", 200);
        Model cid = resolver.dereference(url, Set.of("127.0.0.1"));
        assertTrue(resolver.declaresOpenIdProvider(cid, SUB, ISS));
    }

    @Test
    void dereferencesAndParsesCompactJsonLd() throws IOException {
        String url = serve(JSONLD, "application/ld+json", 200);
        Model cid = resolver.dereference(url, Set.of("127.0.0.1"));
        assertTrue(resolver.declaresOpenIdProvider(cid, SUB, ISS));
    }

    @Test
    void refusesToDereferenceALoopbackHostThatIsNotAllowListed() {
        assertThrows(SsrfGuard.BlockedException.class,
                () -> resolver.dereference("http://127.0.0.1:9/cid", Set.of()));
    }

    @Test
    void aNon200IsACidException() throws IOException {
        String url = serve("nope", "text/plain", 404);
        assertThrows(CidResolver.CidException.class, () -> resolver.dereference(url, Set.of("127.0.0.1")));
    }
}
