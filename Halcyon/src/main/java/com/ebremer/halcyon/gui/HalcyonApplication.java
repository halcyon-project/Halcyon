package com.ebremer.halcyon.gui;

import org.apache.wicket.csp.CSPDirective;
import org.apache.wicket.csp.CSPDirectiveSrcValue;
import com.ebremer.halcyon.sparql.Sparql;
import com.ebremer.halcyon.wicket.Stacks;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.fuseki.SPARQLEndPoint;
import com.ebremer.halcyon.wicket.AccountPage;
import com.ebremer.halcyon.lws.CodeEditorMediaPanel;
import com.ebremer.halcyon.lws.HtmlEditorMediaPanel;
import com.ebremer.halcyon.lws.HtmlMediaPanel;
import com.ebremer.halcyon.lws.ZephyrMediaPanel;
import com.ebremer.halcyon.wicket.ethereal.Graph3D;
import com.ebremer.multiviewer.MultiViewer;
import com.ebremer.ns.HAL;
import com.ebremer.ns.VG;
import com.ebremer.vandegraph.VandegraphApplication;
import com.ebremer.vandegraph.media.MediaBindings;
import org.apache.jena.query.Dataset;
import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.Session;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HalcyonApplication extends VandegraphApplication {
    private final DataCore datacore;
    private final SPARQLEndPoint sep;
    private static final Logger logger = LoggerFactory.getLogger(HalcyonApplication.class);

    public HalcyonApplication() {
        logger.debug("Starting Halcyon UI...");
        datacore = DataCore.getInstance();
        sep = SPARQLEndPoint.getSPARQLEndPoint();
    }

    public DataCore getDataCore() {
        return datacore;
    }

    @Override
    protected Dataset createDataset() {
        // The vandegraph form layers (CommandNode, SelectDataProvider, ...)
        // read/write whatever this returns — Halcyon's data lives in DataCore.
        return datacore.getDataset();
    }

    @Override
    protected boolean ownsDataset() {
        // DataCore manages the dataset lifecycle; don't close it on app destroy.
        return false;
    }

    @Override
    public Class<? extends WebPage> getHomePage() {
	return HomePage.class;
    }

    @Override
    public Session newSession(Request request, Response response) {
        return new HalcyonSession(request,response);
    }

    @Override
    public void init() {
	super.init();
        this.getRequestLoggerSettings().setRequestLoggerEnabled(true);
        this.getRequestLoggerSettings().setRecordSessionSize(true);
        // C5: CSP is ON. It was `blocking().disabled()`, which removed the browser's
        // last line of defence against an injected <script>.
        //
        // The policy is deliberately narrow — `script-src 'nonce-<per-request>' 'self'`
        // and nothing else — because this finding is about SCRIPT injection. Locking
        // down style-src/img-src/connect-src/frame-src as well would be a much larger
        // change (YASGUI injects styles, the viewers fetch tiles from configured hosts,
        // AdminPage frames the Keycloak console) and would trade a real, verified win
        // for a broad risk of breaking pages. Those directives are worth doing next,
        // deliberately, one at a time.
        //
        // What this buys: an injected inline <script> cannot run, because it cannot
        // guess the nonce. That is exactly the C5 chain — the Zephyr stored-Turtle and
        // Upload reflected sinks are already fixed, so this is the defence-in-depth
        // layer that catches the NEXT sink instead of the account being taken over.
        //
        // Why not Wicket's strict(): it emits 'strict-dynamic', which makes browsers
        // IGNORE 'self' — every one of the ~40 external <script src="/..."> tags in the
        // markup would then need a nonce too. 'self' + nonce keeps those working while
        // still refusing anything inline that we did not stamp.
        //
        // Inline scripts in MARKUP get the nonce via CspNonce (Wicket only nonces the
        // header items it renders itself). All 9 are bound: Sparql, Upload, DWVPanel,
        // and the importmap/module pair in each of Zephyr and Graph3D.
        //
        // KNOWN CASUALTY — Graph3D (/threed): its importmap and module load three.js and
        // three-spritetext from //unpkg.com, which 'self' does not cover, so those
        // imports will now be blocked. That page is already unreachable (MenuPanel does
        // `threed.setVisible(false)` and the admin re-enable is commented out), and
        // allow-listing a CDN app-wide to serve one hidden page — while pulling
        // unpinned code from it at runtime — is a bad trade. To bring /threed back:
        // point its importmap at the local /threejs/build/three.module.js (already
        // vendored, and what Zephyr uses) and vendor three-spritetext beside it.
        getCspSettings().blocking()
                .disabled()
                .add(CSPDirective.SCRIPT_SRC, CSPDirectiveSrcValue.NONCE, CSPDirectiveSrcValue.SELF);
        getApplicationSettings().setUploadProgressUpdatesEnabled(true);
        getResourceSettings().setThrowExceptionOnMissingResource(false);
        getDebugSettings().setAjaxDebugModeEnabled(true);
        // H4: enforce page access on the page CLASS, so it holds however the page
        // is reached — mounted path, setResponsePage, or Wicket's default
        // /wicket/bookmarkable/... URL (which no servlet pattern covers). This is
        // the real guard; MenuPanel only ever HID the admin link.
        getSecuritySettings().setAuthorizationStrategy(new HalcyonAuthorizationStrategy());
        // H4: mount from the single PageAccess table that URLControl also derives
        // its secured-URL list from, so the mount table and the security filter
        // cannot drift apart again (they had: the filter guarded "/collections"
        // while Collections was mounted at "/containers").
        //
        // NOTE on /storage: the path must NOT begin with "W3Clws", because Wicket's
        // ignore list is a raw prefix match, so such a page would be excluded from
        // Wicket by the very entries that let the LWS servlets through.
        PageAccess.mounted().forEach(m -> mountPage(m.path(), m.page()));
        // Halcyon's media layer on top of the vandegraph defaults: register
        // the Zephyr wrapper (code — what an IRI does) and overlay the
        // binding shapes (data — which media types get it). Bindings can only
        // select registered viewers, never conjure one.
        getMediaViewers().register(HAL.ZephyrViewer, ZephyrMediaPanel::new);
        getMediaViewers().register(HAL.ZephyrEditor, ZephyrMediaPanel::new);
        // Stored HTML: sandboxed page rendering by default, TipTap editing.
        getMediaViewers().register(HAL.HtmlPageViewer, HtmlMediaPanel::new);
        getMediaViewers().register(HAL.HtmlPageEditor, HtmlEditorMediaPanel::new);
        // Code: vandegraph registers the read-only vg:MonacoViewer itself; the
        // editing IRI is deliberately host-registered because saving is an LWS
        // write (etag-guarded PUT with the user's own token).
        getMediaViewers().register(VG.MonacoEditor, CodeEditorMediaPanel::new);
        setMediaBindings(MediaBindings.parseWithDefaults(halcyonMediaBindings()));
    }

    /** Halcyon's {@code vg:MediaBinding} overlay (classpath {@code halcyon/media-bindings.ttl}). */
    private static org.apache.jena.rdf.model.Model halcyonMediaBindings() {
        org.apache.jena.rdf.model.Model m = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
        try (java.io.InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("halcyon/media-bindings.ttl")) {
            if (in == null) {
                logger.warn("halcyon/media-bindings.ttl not on the classpath; vandegraph defaults only");
            } else {
                org.apache.jena.riot.RDFDataMgr.read(m, in, org.apache.jena.riot.Lang.TURTLE);
            }
        } catch (java.io.IOException e) {
            logger.warn("could not read halcyon media bindings", e);
        }
        return m;
    }
        
    @Override
    public RuntimeConfigurationType getConfigurationType() {
        if (HalcyonSettings.getSettings().isDevMode()) {
            return RuntimeConfigurationType.DEVELOPMENT;
        }
        return RuntimeConfigurationType.DEPLOYMENT;
    }
}
