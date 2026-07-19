package com.ebremer.halcyon.server.mcp;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.mcp.HalcyonSparqlService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The app-side implementation of MCP-9's {@link HalcyonSparqlService}: it runs
 * the (already parsed and bounded) query <strong>as the caller's WebID</strong>
 * against the WAC-secured dataset, and returns the results.
 *
 * <p>This is where the module split pays off. The tool
 * ({@code com.ebremer.halcyon.mcp.SparqlTools}) cannot see the triple store —
 * HalcyonMCP does not (and must not) depend on the app. The app depends on
 * HalcyonMCP, so it implements the interface here and Spring wires it into the
 * tool. The security decision — which triples this WebID may read — stays
 * entirely inside {@code DataCore}/{@code jena-permissions}, reached through
 * {@link DataCore#getSecuredDataset(DataCore.Level, java.security.Principal)}
 * with the caller bound as an explicit {@link HalcyonPrincipal}. An unknown
 * WebID is granted nothing (CLOSED denies), so the query simply returns
 * empty — it can never widen access.
 */
@Configuration
public class McpSparqlConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(McpSparqlConfiguration.class);

    @Bean
    public HalcyonSparqlService halcyonMcpSparqlExecutor() {
        return McpSparqlConfiguration::runReadOnly;
    }

    private static String runReadOnly(String boundedSparql, String callerWebId,
            long maxRows, Duration timeout) {
        Query query = QueryFactory.create(boundedSparql);
        // The caller is authenticated (bearer token); bind their WebID as the
        // WAC identity so the secured dataset scopes to exactly their grants.
        HalcyonPrincipal principal = new HalcyonPrincipal(callerWebId);
        Dataset ds = DataCore.getInstance().getSecuredDataset(DataCore.Level.CLOSED, principal);
        return Txn.calculateRead(ds, () -> {
            try (QueryExecution qe = QueryExecution.dataset(ds).query(query)
                    .timeout(timeout.toMillis(), TimeUnit.MILLISECONDS).build()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                if (query.isAskType()) {
                    ResultSetFormatter.outputAsJSON(out, qe.execAsk());
                } else if (query.isSelectType()) {
                    ResultSet rs = qe.execSelect();
                    ResultSetFormatter.outputAsJSON(out, rs);
                } else if (query.isConstructType() || query.isDescribeType()) {
                    Model m = query.isConstructType() ? qe.execConstruct() : qe.execDescribe();
                    ByteArrayOutputStream ttl = new ByteArrayOutputStream();
                    RDFDataMgr.write(ttl, m, RDFFormat.TURTLE_BLOCKS);
                    return jsonString("turtle", ttl.toString(StandardCharsets.UTF_8));
                } else {
                    return jsonString("error", "unsupported query form");
                }
                return out.toString(StandardCharsets.UTF_8);
            } catch (RuntimeException ex) {
                logger.warn("MCP sparql_query failed for {}: {}", callerWebId, ex.toString());
                return jsonString("error", "query execution failed");
            }
        });
    }

    /** A tiny one-key JSON object with the value escaped. */
    private static String jsonString(String key, String value) {
        return jakarta.json.Json.createObjectBuilder()
                .add(key, value == null ? "" : value)
                .build().toString();
    }
}
