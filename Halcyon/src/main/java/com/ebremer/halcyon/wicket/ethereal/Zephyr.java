package com.ebremer.halcyon.wicket.ethereal;

import com.ebremer.halcyon.gui.CspNonce;
import org.apache.wicket.markup.html.WebMarkupContainer;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.lws.client.LwsClient;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.halcyon.wicket.JsSafe;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.ns.ZEPH;
import jakarta.servlet.http.HttpServletResponse;
import java.io.StringReader;
import java.util.UUID;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
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
 * Zephyr — the RDF-driven stack viewer/editor. LWS-native on both doors:
 * <ul>
 *   <li>{@link Mode#NEW_FROM_IMAGE}: mint a fresh stack URI BESIDE the seed
 *       image in its storage container, seeded with that image as layer 0.
 *       Imagery outside a W3C LWS storage is refused.</li>
 *   <li>{@link Mode#OPEN_STACK}: fetch the stack's relative Turtle over the
 *       LWS API as the signed-in user — ACP is the read authority.</li>
 * </ul>
 * The resolved stack URI is injected as {@code stackUri} for the viewer.
 *
 * @author erich
 */
public class Zephyr extends BasePage {

    private static final Logger logger = LoggerFactory.getLogger(Zephyr.class);
    private static final long serialVersionUID = 102163948377788566L;

    public enum Mode { NEW_FROM_IMAGE, OPEN_STACK }

    private final String target;
    private final String stackUri;
    /**
     * The LWS container the stack file lives in (trailing slash), or
     * {@code null} for a triple-store stack. Injected as {@code stackContainer}
     * so the browser births annotation-layer JSON files BESIDE the stack —
     * the stored Turtle references them relatively (see {@code StackTurtle}),
     * which assumes they share the stack's container.
     */
    private final String stackContainer;
    private final String scenegraph;

    /** New stack seeded from an image (called from the image list). */
    public Zephyr(String imageIri) {
        this(imageIri, Mode.NEW_FROM_IMAGE);
    }

    /**
     * Bookmarkable entry — how the {@code hal:ZephyrViewer} media wrapper
     * (an iframe in the LWSContainers preview) reaches the viewer:
     * {@code ?stack=<uri>} opens an existing stack, {@code ?image=<uri>}
     * seeds a fresh one. No new authority: access is enforced on the page
     * CLASS however it is reached (PageAccess: AUTHENTICATED), and both the
     * open and the save are ACP decisions made by the storage itself.
     */
    public Zephyr(PageParameters params) {
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

    public Zephyr(String uri, Mode mode) {
        this.target = uri;
        Model model;
        if (mode == Mode.OPEN_STACK) {
            // Stacks are LWS resources, full stop: fetched over the LWS API as
            // this user, ACP answering. The triple-store era is over here.
            if (lwsStorageOf(uri) == null) {
                throw new AbortWithHttpErrorCodeException(HttpServletResponse.SC_NOT_FOUND,
                        "stacks live in the W3C LWS storages; this is not a storage URI");
            }
            this.stackUri = uri;
            LwsStack loaded = loadLwsStack(uri);
            model = loaded.model();
            this.stackContainer = loaded.container();
        } else {
            // Seeded from an LWS image: the new stack is minted beside its
            // seed so Save lands it in the same container and the container
            // tree lists it next to the imagery.
            Minted minted = mintLwsStackUri(uri);
            if (minted == null) {
                throw new AbortWithHttpErrorCodeException(HttpServletResponse.SC_BAD_REQUEST,
                        "a stack's imagery must live in a W3C LWS storage");
            }
            this.stackUri = minted.uri();
            this.stackContainer = minted.container();
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
     * the base.
     */
    /** A freshly minted LWS stack: its URI and the container it will live in. */
    private record Minted(String uri, String container) {}

    /** An LWS-resident stack as loaded: its graph and its {@code rel="up"} container. */
    private record LwsStack(Model model, String container) {}

    private static Minted mintLwsStackUri(String imageUri) {
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
        if (!parent.endsWith("/")) {
            parent = parent + "/";
        }
        String name = "stack-" + UUID.randomUUID().toString().substring(0, 8) + ".ttl";
        String stackUri = cfg.naming() == NamingPolicyType.UUID
                ? cfg.baseUri() + "/" + name
                : parent + name;
        return new Minted(stackUri, parent);
    }

    /**
     * Fetch an LWS-resident stack over the API as the signed-in user. No H6
     * check here on purpose: the storage's ACP made the read decision with
     * this user's own token, which is the same authority H6 reimplements for
     * triple-store stacks.
     */
    private static LwsStack loadLwsStack(String uri) {
        LwsClient.Text t = lwsClient().getText(uri, "text/turtle");
        if (!t.ok()) {
            throw new AbortWithHttpErrorCodeException(
                    t.status() == 0 ? HttpServletResponse.SC_BAD_GATEWAY : t.status(),
                    "could not load the stack from its storage");
        }
        Model m = ModelFactory.createDefaultModel();
        try {
            // base = the stack's own URI: the stored document references itself
            // as <> and its container-mates by bare name (see StackTurtle).
            RDFDataMgr.read(m, new StringReader(t.body()), uri, Lang.TURTLE);
        } catch (RuntimeException e) {
            throw new AbortWithHttpErrorCodeException(HttpServletResponse.SC_BAD_GATEWAY,
                    "the stored stack is not parseable Turtle");
        }
        String container = t.link("up");
        if (container == null) {
            int slash = uri.lastIndexOf('/');
            container = slash > 0 ? uri.substring(0, slash + 1) : null;
        } else if (!container.endsWith("/")) {
            container = container + "/";
        }
        return new LwsStack(m, container);
    }

    /** {@code <stackUri> a zeph:Stack ; zeph:layers ( [ zeph:src <imageIri> ] )}. */
    private static Model seedStack(String stackUri, String imageIri) {
        Model m = ModelFactory.createDefaultModel();
        Resource stack = m.createResource(stackUri).addProperty(RDF.type, ZEPH.Stack);
        Resource member = m.createResource().addProperty(ZEPH.src, m.createResource(imageIri));
        stack.addProperty(ZEPH.layers, m.createList(new RDFNode[]{ member }));
        return m;
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
                    save: { enabled: false },
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
        // stackContainer: where the stack file lives (LWS stacks only; "" else).
        // The browser births annotation-layer JSONs there, because the saved
        // stack Turtle references them relative to itself.
        response.render(JavaScriptHeaderItem.forScript(
                "var stackUri = " + JsSafe.jsString(stackUri)
                + "; var baseURI = " + JsSafe.jsString(stackUri)
                + "; var stackContainer = " + JsSafe.jsString(stackContainer) + ";", "stackUri"));
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
