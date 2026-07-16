package com.ebremer.halcyon.wicket.ethereal;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.data.StackStore;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.halcyon.wicket.JsSafe;
import com.ebremer.halcyon.wicket.Stacks;
import com.ebremer.ns.ZEPH;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.wicket.request.http.flow.AbortWithHttpErrorCodeException;

/**
 * Zephyr3 — the RDF-driven stack viewer/editor.
 *
 * Two entry modes:
 * <ul>
 *   <li>{@link Mode#NEW_FROM_IMAGE} (from the image list): mint a fresh stack
 *       URI ({@code <host>/stacks/<uuid>}) and seed it with the chosen image as
 *       layer 0, ready for the user to add more layers and Save.</li>
 *   <li>{@link Mode#OPEN_STACK} (from the Stacks list): load an existing stack's
 *       named graph by URI.</li>
 * </ul>
 * Either way the resolved stack URI is injected as {@code stackUri} so the
 * viewer's "Save stack" writes back to that same per-stack named graph.
 *
 * @author erich
 */
public class Zephyr3 extends BasePage {

    private static final Logger logger = LoggerFactory.getLogger(Zephyr3.class);
    private static final long serialVersionUID = 102163948377788566L;

    public enum Mode { NEW_FROM_IMAGE, OPEN_STACK }

    private final String target;
    private final String stackUri;
    private final String scenegraph;

    /** New stack seeded from an image (called from the image list). */
    public Zephyr3(String imageIri) {
        this(imageIri, Mode.NEW_FROM_IMAGE);
    }

    public Zephyr3(String uri, Mode mode) {
        this.target = uri;
        String host = HalcyonSettings.getSettings().getProxyHostName();
        Model model;
        if (mode == Mode.OPEN_STACK) {
            this.stackUri = uri;
            model = loadGraph(uri);
        } else {
            this.stackUri = host + "/stacks/" + UUID.randomUUID();
            model = seedStack(this.stackUri, uri);
        }
        this.scenegraph = EthTool.serialize(model, this.stackUri);
    }

    /** {@code <stackUri> a zeph:Stack ; zeph:layers ( [ zeph:src <imageIri> ] )}. */
    private static Model seedStack(String stackUri, String imageIri) {
        Model m = ModelFactory.createDefaultModel();
        Resource stack = m.createResource(stackUri).addProperty(RDF.type, ZEPH.Stack);
        Resource member = m.createResource().addProperty(ZEPH.src, m.createResource(imageIri));
        stack.addProperty(ZEPH.layers, m.createList(new RDFNode[]{ member }));
        return m;
    }

    /**
     * Copy an existing stack's named graph out of the triple store, but only for
     * a caller allowed to read it (H6).
     * <p>
     * This was THE escalation: the Stacks page listed every {@code zeph:Stack} in
     * the store and each "view" link came straight here, which copied the named
     * graph out of the RAW dataset — so any signed-in user could open anyone's
     * private stack, and could do so by typing the URL even once the listing was
     * filtered. Authorized with the same admin / {@code schema:creator} /
     * {@code wac:Read} model {@code Stacks} and {@code StackStore} use; the
     * creator is stamped server-side on save, so it cannot be forged.
     */
    private static Model loadGraph(String graphUri) {
        HalcyonPrincipal principal = Stacks.currentPrincipal();
        if (principal == null) {
            throw new AbortWithHttpErrorCodeException(HttpServletResponse.SC_UNAUTHORIZED, "Not signed in");
        }
        Model out = ModelFactory.createDefaultModel();
        Dataset ds = DataCore.getInstance().getDataset();
        ds.begin(ReadWrite.READ);
        try {
            String creator = StackStore.readCreator(ds, graphUri, graphUri);
            boolean allowed = StackStore.canReadStack(principal, graphUri, graphUri, creator,
                    StackStore.readableTargets(principal.getUserURI()));
            if (!allowed) {
                logger.warn("Refusing to open stack {} for {}", graphUri, principal.getUserURI());
                throw new AbortWithHttpErrorCodeException(HttpServletResponse.SC_FORBIDDEN, "No access to this stack");
            }
            out.add(ds.getNamedModel(graphUri));
        } finally {
            ds.end();
        }
        return out;
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        // C5: every value below is JSON-encoded and <>&-hardened via JsSafe. They
        // used to be concatenated between single quotes inside an inline <script>.
        response.render(JavaScriptHeaderItem.forScript("const options = {target: " + JsSafe.jsString(target) + "}", "options"));
        HalcyonSession hs = HalcyonSession.get();
        HalcyonPrincipal hp = hs.getHalcyonPrincipal();
        // C5: the raw Keycloak access token is NO LONGER published to the DOM.
        // The /rdf proxy now attaches it server-side from the signed-in session
        // (see HalcyonProxyServlet), so an XSS can no longer read a live bearer
        // token out of window.token and take the account over.
        // userName/useriri still come from the JWT, i.e. from Keycloak-side data,
        // so they are emitted through JsSafe rather than concatenated.
        response.render(JavaScriptHeaderItem.forScript(
                "var useriri = " + JsSafe.jsString(hp.getUserURI())
                + "; var userName = " + JsSafe.jsString(hp.getPreferredUserName()) + ";", "token"));
        response.render(JavaScriptHeaderItem.forScript(
            """
            var config = {
                  toolbarEnabled: true,
                  tools: {
                    colorPalette: { enabled: true },
                    freeDrawing: { enabled: true },
                    rectangle: { enabled: true },
                    rectangleAlt: { enabled: false },
                    ellipse: { enabled: true },
                    polygon: { enabled: true },
                    hollowBrush: { enabled: true },
                    grid: { enabled: true },
                    edit: { enabled: true },
                    label: { enabled: true },
                    ruler: { enabled: true },
                    screenCapture: { enabled: true },
                    crosshairs: { enabled: true },
                    save: { enabled: true },
                    fetchAnnotations: { enabled: true },
                    zoomControl: { enabled: true },
                    brightContrast: { enabled: true },
                    getImageName: { enabled: true },
                    scaleBar: { enabled: true },
                    minimap: { enabled: true },
                    compare: { enabled: true },
                    featureInfo: { enabled: true }
                  }
                };
            """
            , "config")
        );
        response.render(JavaScriptHeaderItem.forScript(
                "var stackUri = " + JsSafe.jsString(stackUri)
                + "; var baseURI = " + JsSafe.jsString(stackUri) + ";", "stackUri"));
        // C5 (the stored-XSS sink): this was a JS TEMPLATE LITERAL wrapping a
        // stack's saved Turtle — `var scenegraph = ` + "`\n" + scenegraph + "\n`"
        // — so any saved literal containing a backtick or ${ broke straight out
        // and executed when ANOTHER user (or an admin) opened the stack. It is
        // now an ordinary, fully escaped JS string.
        response.render(JavaScriptHeaderItem.forScript("var scenegraph = " + JsSafe.jsString(scenegraph) + ";", "scenegraph"));
    }
}
