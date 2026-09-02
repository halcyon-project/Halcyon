package com.ebremer.halcyon.mcp;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.auth.InvalidBearerTokenException;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP-F1: the whole chain end to end — a real MCP client, over real HTTP on a
 * random port, through the real filter → transport → tool dispatch — with a
 * deterministic auth double in place of Keycloak so the test is hermetic. The
 * assertions are written mutation-style; each names the guard whose removal
 * must break it:
 *
 * <ul>
 *   <li>{@link #noTokenIsRefused} / {@link #badTokenIsRefused} — remove the
 *       MCP-1 filter and an unauthenticated {@code initialize} would succeed.</li>
 *   <li>{@link #goodTokenReachesToolsAsTheCaller} — remove the MCP-2 transport
 *       extractor and {@code halcyon_whoami} can no longer name its caller.</li>
 *   <li>{@link #sparqlUpdateIsRefusedBeforeTheExecutor} — remove the MCP-5
 *       read-only gate and an {@code INSERT} would reach the (absent) executor
 *       and report "not enabled" instead of a parse refusal.</li>
 * </ul>
 */
@SpringBootTest(classes = McpF1IntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.ai.mcp.server.protocol=STREAMABLE",
            "spring.ai.mcp.server.name=halcyon-it",
            "spring.main.banner-mode=off"
        })
class McpF1IntegrationTest {

    static final String GOOD_TOKEN = "good-alice";
    static final String ALICE = "https://it.example/user/alice#me";

    @LocalServerPort
    int port;

    /** A minimal app: auto-config (which pulls in the MCP server + tools) plus
     *  the deterministic auth double. */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {

        @Bean
        McpBearerAuth mcpBearerAuth() {
            return new TestBearerAuth();
        }
    }

    /** Accepts exactly {@link #GOOD_TOKEN}; refuses everything else — no
     *  network, no Keycloak. Mirrors the real refusal semantics the filter
     *  depends on (anonymous → no error code, bad → invalid_token). */
    static final class TestBearerAuth extends McpBearerAuth {
        TestBearerAuth() {
            super(() -> {
                throw new IllegalStateException("the integration test must not do OIDC discovery");
            });
        }

        @Override
        public AgentContext authenticate(HttpServletRequest req) {
            String header = req.getHeader("Authorization");
            if (header == null || header.isBlank()) {
                throw new InvalidBearerTokenException(null, "authentication required");
            }
            if (("Bearer " + GOOD_TOKEN).equals(header)) {
                return new AgentContext(ALICE, "it-client",
                        "https://it.example/realms/Halcyon", List.of());
            }
            throw new InvalidBearerTokenException("invalid_token", "the access token is not valid");
        }

        @Override
        public String resource() {
            return "http://localhost/mcp";
        }

        @Override
        public String challenge(String error) {
            String c = "Bearer realm=\"http://localhost/mcp\"";
            return error == null || error.isBlank() ? c : c + ", error=\"" + error + "\"";
        }
    }

    private McpSyncClient client(String token) {
        McpSyncHttpClientRequestCustomizer bearer = (builder, method, uri, body, context) -> {
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
        };
        HttpClientStreamableHttpTransport transport =
                HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                        .endpoint("/mcp")
                        .httpRequestCustomizer(bearer)
                        .build();
        return McpClient.sync(transport)
                .initializationTimeout(Duration.ofSeconds(20))
                .requestTimeout(Duration.ofSeconds(20))
                .build();
    }

    private static String textOf(CallToolResult result) {
        StringBuilder sb = new StringBuilder();
        for (Content c : result.content()) {
            if (c instanceof TextContent t) {
                sb.append(t.text());
            }
        }
        return sb.toString();
    }

    @Test
    void noTokenIsRefused() {
        try (McpSyncClient c = client(null)) {
            assertThrows(Exception.class, c::initialize,
                    "an unauthenticated initialize must not succeed (MCP-1)");
        }
    }

    @Test
    void badTokenIsRefused() {
        try (McpSyncClient c = client("not-the-token")) {
            assertThrows(Exception.class, c::initialize,
                    "an invalid token must not authenticate (MCP-1)");
        }
    }

    @Test
    void goodTokenReachesToolsAsTheCaller() {
        try (McpSyncClient c = client(GOOD_TOKEN)) {
            c.initialize();

            List<io.modelcontextprotocol.spec.McpSchema.Tool> toolList = c.listTools().tools();
            Set<String> tools = toolList.stream()
                    .map(io.modelcontextprotocol.spec.McpSchema.Tool::name)
                    .collect(Collectors.toSet());
            assertTrue(tools.containsAll(Set.of("halcyon_whoami", "lws_storages",
                    "lws_list", "lws_read", "sparql_query", "find_slides", "iiif_info",
                    "lws_put", "lws_request_access")),
                    "the full tool surface must be listed, got: " + tools);

            // The schema property names must be the real argument names, not
            // arg0/arg1 — i.e. the module compiled with -parameters. A client
            // calls tools by these names; getting them wrong makes every
            // multi-arg tool uncallable.
            String sparqlSchema = toolList.stream()
                    .filter(t -> "sparql_query".equals(t.name())).findFirst().orElseThrow()
                    .inputSchema().toString();
            assertTrue(sparqlSchema.contains("sparql"),
                    "the sparql_query arg must be named 'sparql', not arg0: " + sparqlSchema);
            assertFalse(sparqlSchema.contains("arg0"),
                    "reflective arg names (arg0) mean -parameters is off: " + sparqlSchema);

            // MCP-2: the tool sees the caller the token proved — end to end
            // through the transport context, not a thread-local.
            CallToolResult who = c.callTool(new CallToolRequest("halcyon_whoami", Map.of()));
            assertTrue(textOf(who).contains("alice#me"),
                    "whoami must echo the verified caller, got: " + textOf(who));
        }
    }

    @Test
    void sparqlUpdateIsRefusedBeforeTheExecutor() {
        try (McpSyncClient c = client(GOOD_TOKEN)) {
            c.initialize();

            // An UPDATE is refused by the Guardrails gate (MCP-5) — it never
            // reaches the executor. A valid SELECT, by contrast, reaches the
            // gate and reports the executor absent (no HalcyonSparqlService in
            // this slice). The two different messages prove the gate ran.
            String update = textOf(c.callTool(new CallToolRequest("sparql_query",
                    Map.of("sparql", "INSERT DATA { <urn:a> <urn:b> <urn:c> }"))));
            assertFalse(update.contains("not enabled"),
                    "an update must be refused by the read-only gate, not passed to the executor");
            assertTrue(update.toLowerCase().contains("update")
                    || update.toLowerCase().contains("not a valid"),
                    "the refusal must name the read-only violation, got: " + update);

            String select = textOf(c.callTool(new CallToolRequest("sparql_query",
                    Map.of("sparql", "SELECT * WHERE { ?s ?p ?o }"))));
            assertTrue(select.contains("not enabled"),
                    "a valid query passes the gate and finds no executor in this slice, got: " + select);
        }
    }
}
