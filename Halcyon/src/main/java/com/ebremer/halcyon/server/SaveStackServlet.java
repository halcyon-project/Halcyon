package com.ebremer.halcyon.server;

import com.ebremer.halcyon.data.StackStore;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authenticated, WAC-gated server-side stack-save endpoint ({@code POST /savestack}).
 * <p>
 * Replaces the browser's former raw SPARQL Update to {@code /rdf} (closed by H1,
 * which made the Fuseki endpoint read-only): persisting a Zephyr stack now goes
 * through this endpoint. It authenticates the caller from the pac4j OIDC session
 * (never a bare bearer token) and delegates the write to {@link StackStore},
 * whose creator/WAC/admin authorization is the same one the {@code Stacks} page
 * enforces for delete.
 * <p>
 * Request: {@code POST /savestack?graph=<stack-uri>}, body = the stack RDF as
 * N-Triples. The {@code graph} parameter is the stack's named graph and its root
 * subject; the payload must be a {@code zeph:Stack} rooted at that URI.
 *
 * @author erich
 */
public class SaveStackServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(SaveStackServlet.class);

    /** Cap on the request body — a stack graph is a few KB; this is generous. */
    private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HalcyonPrincipal principal = RequestPrincipal.resolve(request, response);
        if (!RequestPrincipal.isSignedIn(principal)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not signed in");
            return;
        }
        String graph = request.getParameter("graph");
        if (graph == null || graph.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'graph' parameter");
            return;
        }

        // Read the body under a hard size cap (readNBytes stops at the limit, so
        // a chunked body with an absent or lying Content-Length cannot exhaust memory).
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Stack too large");
            return;
        }

        Model incoming = ModelFactory.createDefaultModel();
        try {
            RDFDataMgr.read(incoming, new ByteArrayInputStream(body), Lang.NTRIPLES);
        } catch (RuntimeException ex) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed RDF payload");
            return;
        }

        try {
            StackStore.Result result = StackStore.save(graph, incoming, principal);
            switch (result) {
                case SAVED -> response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                case FORBIDDEN -> response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Not allowed to write this stack");
                case INVALID -> response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Invalid stack save target");
            }
        } catch (RuntimeException ex) {
            logger.error("Stack save failed for {}", graph, ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Stack save failed");
        }
    }

}
