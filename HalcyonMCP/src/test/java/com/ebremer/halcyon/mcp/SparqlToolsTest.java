package com.ebremer.halcyon.mcp;

import com.ebremer.lws.auth.AgentContext;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import jakarta.json.Json;
import java.io.StringReader;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP-9 tool contract: refuse without a caller; fail closed with no executor;
 * refuse updates/SERVICE via the Guardrails gate BEFORE any executor call
 * (the executor must never see a non-read-only query); and, with an executor,
 * delegate the bounded query as the caller's WebID. Execution itself (the WAC
 * scoping) is the app bean's job and is runtime-verified.
 */
class SparqlToolsTest {

    private static final AgentContext ALICE = new AgentContext(
            "https://localhost:8888/user/alice#me", "cli", "iss", List.of());

    private static ToolContext callerContext() {
        McpSyncServerExchange ex = Mockito.mock(McpSyncServerExchange.class);
        Mockito.when(ex.transportContext()).thenReturn(McpTransportContext.create(
                Map.of(McpBearerAuthFilter.CALLER_ATTRIBUTE, new McpCaller(ALICE, "tok"))));
        return new ToolContext(Map.of(McpToolUtils.TOOL_CONTEXT_MCP_EXCHANGE_KEY, ex));
    }

    /** An ObjectProvider yielding a fixed value (or none). */
    private static ObjectProvider<HalcyonSparqlService> provider(HalcyonSparqlService svc) {
        @SuppressWarnings("unchecked")
        ObjectProvider<HalcyonSparqlService> p = Mockito.mock(ObjectProvider.class);
        Mockito.when(p.getIfAvailable()).thenReturn(svc);
        return p;
    }

    private static String errorOf(String json) {
        try (var r = Json.createReader(new StringReader(json))) {
            return r.readObject().getString("error", null);
        }
    }

    @Test
    void refusesWithoutAVerifiedCaller() {
        SparqlTools tool = new SparqlTools(provider((q, w, r, t) -> "{}"));
        assertThrows(IllegalStateException.class,
                () -> tool.query("SELECT * WHERE { ?s ?p ?o }", new ToolContext(Map.of())));
    }

    @Test
    void failsClosedWhenNoExecutorIsRegistered() {
        SparqlTools tool = new SparqlTools(provider(null));
        String out = tool.query("SELECT * WHERE { ?s ?p ?o }", callerContext());
        assertTrue(errorOf(out).contains("not enabled"),
                "no executor must mean SPARQL is reported disabled, got: " + out);
    }

    @Test
    void updatesAndServiceAreRefusedBeforeReachingTheExecutor() {
        AtomicReference<String> sawQuery = new AtomicReference<>();
        SparqlTools tool = new SparqlTools(provider((q, w, r, t) -> {
            sawQuery.set(q);
            return "{}";
        }));
        for (String bad : List.of(
                "INSERT DATA { <urn:a> <urn:b> <urn:c> }",
                "SELECT * WHERE { SERVICE <http://internal> { ?s ?p ?o } }")) {
            String out = tool.query(bad, callerContext());
            assertTrue(errorOf(out) != null, bad + " must be refused: " + out);
        }
        assertNull(sawQuery.get(), "the executor must never see a refused query");
    }

    @Test
    void delegatesTheBoundedQueryAsTheCallerWebId() {
        AtomicReference<String> gotQuery = new AtomicReference<>();
        AtomicReference<String> gotWebId = new AtomicReference<>();
        AtomicReference<Duration> gotTimeout = new AtomicReference<>();
        SparqlTools tool = new SparqlTools(provider((q, webid, rows, timeout) -> {
            gotQuery.set(q);
            gotWebId.set(webid);
            gotTimeout.set(timeout);
            return "{\"head\":{},\"results\":{\"bindings\":[]}}";
        }));
        String out = tool.query("SELECT * WHERE { ?s ?p ?o }", callerContext());

        assertEquals("https://localhost:8888/user/alice#me", gotWebId.get(),
                "the query must run as the caller's WebID, not the server");
        assertTrue(gotQuery.get().toUpperCase().contains("LIMIT"),
                "the query handed to the executor must carry the injected LIMIT: " + gotQuery.get());
        assertEquals(Guardrails.QUERY_TIMEOUT, gotTimeout.get());
        assertTrue(out.contains("bindings"), "the executor's result must be returned: " + out);
    }
}
