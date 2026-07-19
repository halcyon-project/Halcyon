package com.ebremer.halcyon.server;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.lws.client.LwsClient;
import com.ebremer.lws.config.LwsStorageConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringReader;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code GET /colorclasses} — the Zephyr palette's source for the signed-in
 * user's annotation color classes, now an LWS resource
 * ({@link ColorClassesStore}).
 *
 * <p>A relay for the same reason the LWS Containers preview has one: the
 * browser cannot attach a bearer token (C5 keeps it out of the DOM), so this
 * endpoint reads the ACP-protected resource server-side with the session's
 * own token and answers plain JSON {@code [{"name":…,"color":…}]}. An empty
 * array — including the not-created-yet 404 — or any error makes the palette
 * fall back to its built-in defaults. (No legacy-store migration: the old
 * dataset was checked and held no color classes for any user.)
 */
public class ColorClassesServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ColorClassesServlet.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HalcyonPrincipal hp = RequestPrincipal.resolve(request, response);
        if (!RequestPrincipal.isSignedIn(hp) || hp.getPreferredUserName() == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not signed in");
            return;
        }
        LwsStorageConfig cfg = ColorClassesStore.storage();
        if (cfg == null) {
            // No LWS storage for user data: the palette just uses its defaults.
            json(response, "[]");
            return;
        }
        String user = hp.getPreferredUserName();
        String uri = ColorClassesStore.documentUri(cfg, user);
        LwsClient client = new LwsClient(hp.getToken(),
                com.ebremer.halcyon.server.utils.HalcyonSettings.getSettings().getProxyHostName());

        LwsClient.Text t = client.getText(uri, "text/turtle");
        if (t.ok()) {
            Model m = ModelFactory.createDefaultModel();
            RDFDataMgr.read(m, new StringReader(t.body() == null ? "" : t.body()), uri, Lang.TURTLE);
            json(response, ColorClassesStore.toJson(ColorClassesStore.rows(m)));
            return;
        }
        if (t.status() == 404) {
            // Not created yet — the palette uses its defaults until the user
            // defines classes in the editor.
            json(response, "[]");
            return;
        }
        logger.warn("colorclasses read of {} answered HTTP {}", uri, t.status());
        response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "storage unavailable");
    }

    private static void json(HttpServletResponse response, String body) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(body);
    }
}
