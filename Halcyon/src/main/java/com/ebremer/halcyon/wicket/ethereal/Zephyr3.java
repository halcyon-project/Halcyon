package com.ebremer.halcyon.wicket.ethereal;

import com.ebremer.halcyon.gui.CspNonce;
import org.apache.wicket.markup.html.WebMarkupContainer;
import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.data.StackStore;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.lws.LwsClient;
import com.ebremer.halcyon.server.SaveStackServlet;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.halcyon.wicket.JsSafe;
import com.ebremer.halcyon.wicket.Stacks;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.ns.ZEPH;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.StringReader;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.request.mapper.parameter.PageParameters;
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

    /**
     * Bookmarkable entry — how the {@code hal:ZephyrViewer} media wrapper
     * (an iframe in the LWSContainers preview) reaches the viewer:
     * {@code ?stack=<uri>} opens an existing stack, {@code ?image=<uri>}
     * seeds a fresh one. No new authority: access is enforced on the page
     * CLASS however it is reached (PageAccess: AUTHENTICATED), OPEN_STACK
     * still runs the H6 read check in {@link #loadGraph}, and Save still
     * authorizes through StackStore.
     */
    public Zephyr3(PageParameters params) {
        this(targetOf(params), hasStack(params) ? Mode.OPEN_STACK : Mode.NEW_FROM_IMAGE);
    }

    private static boolean hasStack(PageParameters params) {
        String s = params.get("stack").toOptionalString();
        return s != null && !s.isBlank();
    }

    private static String targetOf(PageParameters params) {
        if (hasStack(params)) {
            return params.get("stack").toOptionalString();
        }
        String image = params.get("image").toOptionalString();
        if (image != null && !image.isBlank()) {
            return image;
        }
        throw new AbortWithHttpErrorCodeException(HttpServletResponse.SC_BAD_REQUEST,
                "a stack or image parameter is required");
    }

    public Zephyr3(String uri, Mode mode) {
        this.target = uri;
        String host = HalcyonSettings.getSettings().getProxyHostName();
        Model model;
        if (mode == Mode.OPEN_STACK) {
            this.stackUri = uri;
            // A stack living in an LWS storage is fetched over the LWS API as
            // this user (ACP answers); a triple-store stack keeps the H6 check.
            model = lwsStorageOf(uri) != null ? loadLwsStack(uri) : loadGraph(uri);
        } else {
            // Seeded from an LWS image, the new stack is LWS-NATIVE: minted
            // beside its seed so Save lands it in the same container and the
            // container tree lists it next to the imagery. Otherwise the
            // classic triple-store URI.
            String lwsStack = mintLwsStackUri(uri);
            this.stackUri = lwsStack != null ? lwsStack : host + "/stacks/" + UUID.randomUUID();
            model = seedStack(this.stackUri, uri);
        }
        this.scenegraph = EthTool.serialize(model, this.stackUri);
    }

    /** The configured storage a URI belongs to, or {@code null}. */
    private static LwsStorageConfig lwsStorageOf(String uri) {
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            if (uri.startsWith(cfg.baseUri() + "/")) {
                return cfg;
            }
        }
        return null;
    }

    private static LwsClient lwsClient() {
        HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
        String token = hp == null || hp.isAnon() ? null : hp.getToken();
        return new LwsClient(token, HalcyonSettings.getSettings().getProxyHostName());
    }

    /**
     * Mint an LWS-native stack URI for a stack seeded from an LWS image, or
     * {@code null} when the image is not an LWS resource. The stack goes in
     * the image's own container (discovered from the API's {@code rel="up"}
     * link); in the flat storage the URI never nests, so the name hangs off
     * the base while the container is remembered for the create — stashed in
     * the HTTP session for {@link SaveStackServlet} to consume on first save.
     */
    private static String mintLwsStackUri(String imageUri) {
        LwsStorageConfig cfg = lwsStorageOf(imageUri);
        if (cfg == null) {
            return null;
        }
        String parent = null;
        try {
            parent = lwsClient().head(imageUri).link("up");
        } catch (RuntimeException e) {
            logger.warn("could not discover the container of {}: {}", imageUri, e.toString());
        }
        if (parent == null) {
            parent = cfg.storageRootUri();
        }
        String name = "stack-" + UUID.randomUUID().toString().substring(0, 8) + ".ttl";
        String stackUri = cfg.naming() == NamingPolicyType.UUID
                ? cfg.baseUri() + "/" + name
                : (parent.endsWith("/") ? parent + name : parent + "/" + name);
        stashPendingContainer(stackUri, parent);
        return stackUri;
    }

    /** Remember which container a not-yet-created LWS stack should be POSTed into. */
    private static void stashPendingContainer(String stackUri, String container) {
        if (RequestCycle.get().getRequest().getContainerRequest()
                instanceof HttpServletRequest hr) {
            var session = hr.getSession(true);
            synchronized (session) {
                @SuppressWarnings("unchecked")
                Map<String, String> map = (Map<String, String>)
                        session.getAttribute(SaveStackServlet.PENDING_LWS_STACKS);
                if (map == null) {
                    map = new ConcurrentHashMap<>();
                    session.setAttribute(SaveStackServlet.PENDING_LWS_STACKS, map);
                }
                map.put(stackUri, container);
            }
        }
    }

    /**
     * Fetch an LWS-resident stack over the API as the signed-in user. No H6
     * check here on purpose: the storage's ACP made the read decision with
     * this user's own token, which is the same authority H6 reimplements for
     * triple-store stacks.
     */
    private static Model loadLwsStack(String uri) {
        LwsClient.Text t = lwsClient().getText(uri, "text/turtle");
        if (!t.ok()) {
            throw new AbortWithHttpErrorCodeException(
                    t.status() == 0 ? HttpServletResponse.SC_BAD_GATEWAY : t.status(),
                    "could not load the stack from its storage");
        }
        Model m = ModelFactory.createDefaultModel();
        try {
            RDFDataMgr.read(m, new StringReader(t.body()), uri, Lang.TURTLE);
        } catch (RuntimeException e) {
            throw new AbortWithHttpErrorCodeException(HttpServletResponse.SC_BAD_GATEWAY,
                    "the stored stack is not parseable Turtle");
        }
        return m;
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

    /**
     * C5: bind the inline <script> tags in this page's markup so they receive the
     * request's CSP nonce. Done in onInitialize rather than a constructor because
     * these classes have several constructors that do not delegate to one another —
     * onInitialize runs exactly once whichever was used.
     */
    @Override
    protected void onInitialize() {
        super.onInitialize();
        add(new WebMarkupContainer("cspImportMap").add(new CspNonce()));
        add(new WebMarkupContainer("cspModule").add(new CspNonce()));
    }
}
