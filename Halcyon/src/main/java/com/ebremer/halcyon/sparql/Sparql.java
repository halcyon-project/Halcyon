package com.ebremer.halcyon.sparql;

import com.ebremer.halcyon.gui.CspNonce;
import org.apache.wicket.markup.html.WebMarkupContainer;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.fuseki.shiro.JwtVerifier;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.halcyon.wicket.JsSafe;
import io.jsonwebtoken.Claims;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.CssResourceReference;
import org.apache.wicket.request.resource.JavaScriptResourceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The YASGUI query page, parameterized over the TWO SPARQL endpoints:
 * {@code /sparql} targets the classic store through {@code /rdf} (default),
 * {@code /sparql?endpoint=rdf2} targets the W3C LWS module's own store
 * through {@code /rdf2}. The page carries a picker to switch; each endpoint
 * gets its own YASGUI persistence namespace, so tabs opened against one store
 * never resurface pointing at the other.
 *
 * @author erich
 */
//https://triply.cc/docs/yasgui-api
public class Sparql extends BasePage {

    private static final Logger logger = LoggerFactory.getLogger(Sparql.class);

    /** Where YASGUI points. Allowlisted — this string lands inside a script. */
    private final String endpoint;

    public Sparql() {
        this(new PageParameters());
    }

    /**
     * The parameter is MAPPED, never echoed: whatever arrives, the only
     * strings that can reach the page are the two constants below.
     */
    public Sparql(PageParameters parameters) {
        this.endpoint = "rdf2".equals(parameters.get("endpoint").toString(""))
                ? "/rdf2" : "/rdf";
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(new CssResourceReference(Sparql.class, "yasgui.min.css")));
        response.render(JavaScriptHeaderItem.forReference(new JavaScriptResourceReference(Sparql.class, "yasgui.min.js")));
        // The chosen endpoint (one of two constants — see the constructor) and
        // a per-endpoint persistence namespace, read by the inline YASGUI
        // config in the markup. Header items render before the body script.
        response.render(JavaScriptHeaderItem.forScript(
                "var sparqlEndpoint = " + JsSafe.jsString(endpoint)
                + "; var sparqlPersistenceId = "
                + JsSafe.jsString("yasgui" + endpoint.replace('/', '_')) + ";",
                "sparql-endpoint"));
        HalcyonSession hs = HalcyonSession.get();
        HalcyonPrincipal hp = hs.getHalcyonPrincipal();
        // C5: this page also published the raw access token — "var token = '<jwt>'"
        // — which Sparql.html then put on every YASGUI request. Both are gone: the
        // /rdf proxy attaches the bearer server-side from the session. The session-
        // expiry warning is kept, but it only ever needed the EXPIRY INSTANT, so
        // that is all we emit now (a number, decoded server-side) instead of a
        // live credential any XSS on this page could have lifted.
        String tokenScript = String.format(
                """
                var useriri = %s;
                var userName = %s;
                var tokenExpiry = %d;
                function checkToken() {
                    if (tokenExpiry > 0 && Date.now() > tokenExpiry) {
                        alert('Your session has expired. Please log in again.');
                        window.location.href = '/invalidateSession';
                    }
                }
                setInterval(checkToken, 60000);
                """,
                JsSafe.jsString(hp.getUserURI()),
                JsSafe.jsString(hp.getPreferredUserName()),
                tokenExpiryMillis(hp)
        );
        response.render(JavaScriptHeaderItem.forScript(tokenScript, "token-check"));
    }

    /**
     * The caller's access-token expiry as epoch millis, or 0 if it cannot be
     * determined (in which case the client simply never fires the warning).
     * Decoding happens here so the token itself stays out of the page.
     */
    private static long tokenExpiryMillis(HalcyonPrincipal hp) {
        try {
            Claims claims = new JwtVerifier().verify(hp.getToken());
            if (claims != null && claims.getExpiration() != null) {
                return claims.getExpiration().getTime();
            }
        } catch (Exception ex) {
            logger.debug("Could not read token expiry: {}", ex.getMessage());
        }
        return 0L;
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
        add(new WebMarkupContainer("cspSparql").add(new CspNonce()));
        add(new Label("which", "/rdf2".equals(endpoint)
                ? "the W3C LWS store (/rdf2)" : "the Halcyon store (/rdf)"));
        // The picker: the current endpoint's link is disabled, which is also
        // what marks it visually (see the a:not([href]) rule in the markup).
        BookmarkablePageLink<Void> classic = new BookmarkablePageLink<>("classic", Sparql.class);
        classic.setEnabled("/rdf2".equals(endpoint));
        add(classic);
        BookmarkablePageLink<Void> lws = new BookmarkablePageLink<>("lws", Sparql.class,
                new PageParameters().add("endpoint", "rdf2"));
        lws.setEnabled("/rdf".equals(endpoint));
        add(lws);
    }
}
