package com.ebremer.halcyon.mcp;

import java.time.Duration;

/**
 * MCP-9's execution seam. The SPARQL tool ({@link SparqlTools}) lives here, in
 * a module that <em>cannot</em> see the triple store — {@code DataCore} and
 * the {@code jena-permissions} WAC layer are in the Halcyon app, and the app
 * depends on this module, not the other way round. So execution is an
 * interface the app implements and registers as a bean; the tool parses and
 * bounds the query (MCP-5) and delegates here.
 *
 * <p>The contract an implementation MUST honour — this is the security
 * boundary, so it is stated, not assumed:
 * <ul>
 *   <li>Run the query <strong>as {@code callerWebId}</strong> against the
 *       <strong>secured</strong> dataset — the WAC layer decides which triples
 *       that WebID may see. The server's own identity must never be the one
 *       asking.</li>
 *   <li><strong>Fail closed</strong> for an unknown or unauthorized WebID:
 *       empty results, never an error and never a fallback to a broader
 *       view.</li>
 *   <li>Enforce {@code timeout} on execution and treat {@code maxRows} as a
 *       hard ceiling (the query already carries an injected {@code LIMIT}; this
 *       is belt-and-suspenders).</li>
 * </ul>
 *
 * <p>The query string handed in has already passed
 * {@link Guardrails#readOnlyQuery} — read-only, no {@code SERVICE}, bounded.
 * When no implementation is registered the tool refuses (SPARQL disabled),
 * which is the safe default.
 *
 * @see SparqlTools
 */
public interface HalcyonSparqlService {

    /**
     * Execute a validated read-only query as {@code callerWebId} and return
     * the results as a JSON string: SPARQL Results JSON for {@code SELECT}/
     * {@code ASK}, or {@code {"turtle": "..."}} for {@code CONSTRUCT}/
     * {@code DESCRIBE}.
     */
    String runReadOnly(String boundedSparql, String callerWebId, long maxRows, Duration timeout);
}
