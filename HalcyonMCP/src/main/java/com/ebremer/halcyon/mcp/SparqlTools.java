package com.ebremer.halcyon.mcp;

import jakarta.json.Json;
import org.apache.jena.query.Query;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;

/**
 * MCP-9: {@code sparql_query} — read-only SPARQL against Halcyon's secured RDF
 * dataset, run as the calling user. The tool owns the two guards; the
 * {@link HalcyonSparqlService} (app-provided) owns execution as the caller's
 * WebID:
 *
 * <ol>
 *   <li>The query is parsed and bounded by {@link Guardrails#readOnlyQuery} —
 *       updates and {@code SERVICE} do not survive, {@code LIMIT} is injected
 *       or clamped. This is the C2 lesson made mechanical (see
 *       {@link Guardrails}).</li>
 *   <li>Execution is delegated to the secured executor, which runs it as the
 *       caller's WebID against the WAC-secured dataset — the caller sees only
 *       what ACP grants their WebID. No executor registered → SPARQL is
 *       reported disabled, the safe default.</li>
 * </ol>
 */
public class SparqlTools {

    private final ObjectProvider<HalcyonSparqlService> executor;

    SparqlTools(ObjectProvider<HalcyonSparqlService> executor) {
        this.executor = executor;
    }

    @Tool(name = "sparql_query",
            description = "Run a read-only SPARQL query (SELECT/ASK/CONSTRUCT/DESCRIBE) over "
                    + "Halcyon's RDF dataset as the calling user - you see only triples ACP "
                    + "grants your WebID. Updates and SERVICE clauses are refused; results are "
                    + "row-capped and time-bounded. Returns SPARQL Results JSON, or Turtle for "
                    + "CONSTRUCT/DESCRIBE.")
    public String query(
            @ToolParam(description = "A read-only SPARQL query (no updates, no SERVICE).")
            String sparql,
            ToolContext toolContext) {
        McpCaller caller = McpCallers.require(toolContext);

        HalcyonSparqlService svc = executor.getIfAvailable();
        if (svc == null) {
            return err("SPARQL querying is not enabled on this server");
        }

        Query q;
        try {
            q = Guardrails.readOnlyQuery(sparql, Guardrails.MAX_ROWS);
        } catch (IllegalArgumentException e) {
            // A refused query (update, SERVICE, garbage) is the caller's fault,
            // reported plainly — not a server error.
            return err(e.getMessage());
        }

        try {
            return svc.runReadOnly(q.toString(), caller.webId(),
                    Guardrails.MAX_ROWS, Guardrails.QUERY_TIMEOUT);
        } catch (RuntimeException e) {
            return err("query execution failed: " + e.getMessage());
        }
    }

    private static String err(String message) {
        return Json.createObjectBuilder().add("error", message).build().toString();
    }
}
