package com.ebremer.halcyon.server;

import com.ebremer.halcyon.data.StackStore;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.lws.LwsClient;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.ns.ZEPH;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
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

    /**
     * HTTP-session attribute: {@code Map<String,String>} of not-yet-created
     * LWS stack URIs → the container to POST them into. Zephyr3 stashes the
     * pair when it mints an LWS-native stack URI (the flat storage's URIs do
     * not encode their container); the first save consumes it.
     */
    public static final String PENDING_LWS_STACKS = "halcyon.lws.pendingStacks";

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

        // An LWS-native stack (its URI lies in a configured storage) is saved
        // THROUGH the storage's API with the user's own token — ACP authorizes,
        // the storage records ownership, and the container tree lists it beside
        // its imagery. Triple-store stacks keep the StackStore path unchanged.
        LwsStorageConfig cfg = storageOf(graph);
        if (cfg != null) {
            saveToStorage(cfg, graph, incoming, principal, request, response);
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

    /** The configured storage a URI belongs to, or {@code null}. */
    private static LwsStorageConfig storageOf(String uri) {
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            if (uri.startsWith(cfg.baseUri() + "/")) {
                return cfg;
            }
        }
        return null;
    }

    /**
     * Persist an LWS-native stack through the storage's own API: a conditional
     * {@code PUT} when the resource exists, else a {@code POST} into the
     * container Zephyr3 stashed when it minted the URI. The user's own bearer
     * token makes the request, so the storage's ACP is the authorization —
     * this endpoint adds none of its own beyond the sanity checks.
     */
    private void saveToStorage(LwsStorageConfig cfg, String graph, Model incoming,
            HalcyonPrincipal principal, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Resource root = ResourceFactory.createResource(graph);
        if (!incoming.contains(root, RDF.type, ZEPH.Stack)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "the payload must be a zeph:Stack rooted at the stack URI");
            return;
        }
        // Server-stamped ownership, same discipline as StackStore.save: the
        // client's claim is dropped and the signed-in user recorded.
        org.apache.jena.rdf.model.Property creator = ResourceFactory.createProperty(
                org.apache.jena.vocabulary.SchemaDO.NS + "creator");
        incoming.removeAll(root, creator, null);
        if (principal.getUserURI() != null) {
            incoming.add(root, creator, incoming.createResource(principal.getUserURI()));
        }
        // Stored RELATIVE (see StackTurtle): the document names itself <> and
        // its same-container companions — imagery, annotation-layer JSONs — by
        // bare sibling name, so it inherits the URI it is served from and the
        // container can move without rewriting the stacks inside it.
        byte[] bytes = StackTurtle.relative(incoming, graph).getBytes(StandardCharsets.UTF_8);

        LwsClient client = new LwsClient(principal.getToken(),
                HalcyonSettings.getSettings().getProxyHostName());
        String etag = client.etag(graph);
        LwsClient.Result r;
        if (etag != null) {
            r = client.put(graph, "text/turtle", bytes, etag);
        } else {
            String container = pendingContainer(request, graph);
            if (container == null && cfg.naming() != com.ebremer.lws.config.NamingPolicyType.UUID) {
                // Hierarchical URIs encode their container; recover it.
                int slash = graph.lastIndexOf('/');
                container = slash > 0 ? graph.substring(0, slash + 1) : null;
            }
            if (container == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "no destination container is known for this new stack");
                return;
            }
            String slug = graph.substring(graph.lastIndexOf('/') + 1);
            r = client.post(container, slug, "text/turtle", bytes, false);
            if (r.ok() && r.location() != null && !graph.equals(r.location())) {
                // The flat storage honors slugs best-effort; a collision would
                // strand a document whose root subject is not its URI.
                logger.error("stack landed at {} but its root subject is {}", r.location(), graph);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "the storage assigned a different URI than the stack was built for");
                return;
            }
        }
        if (r.ok()) {
            clearPending(request, graph);
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } else {
            String title = r.body() == null ? null : r.body().getString("title", null);
            int status = switch (r.status()) {
                case 401, 403 -> HttpServletResponse.SC_FORBIDDEN;
                case 412, 428 -> HttpServletResponse.SC_CONFLICT;
                case 0 -> HttpServletResponse.SC_BAD_GATEWAY;
                default -> r.status();
            };
            logger.warn("LWS stack save of {} answered HTTP {} {}", graph, r.status(),
                    title == null ? "" : title);
            response.sendError(status, title == null ? "the storage refused the save" : title);
        }
    }

    private static String pendingContainer(HttpServletRequest request, String graph) {
        var session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object o = session.getAttribute(PENDING_LWS_STACKS);
        return o instanceof Map<?, ?> map && map.get(graph) instanceof String s ? s : null;
    }

    private static void clearPending(HttpServletRequest request, String graph) {
        var session = request.getSession(false);
        if (session != null && session.getAttribute(PENDING_LWS_STACKS) instanceof Map<?, ?> map) {
            map.remove(graph);
        }
    }
}
