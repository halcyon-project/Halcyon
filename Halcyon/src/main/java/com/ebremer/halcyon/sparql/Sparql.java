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
import org.apache.wicket.request.resource.CssResourceReference;
import org.apache.wicket.request.resource.JavaScriptResourceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
//https://triply.cc/docs/yasgui-api
public class Sparql extends BasePage {

    private static final Logger logger = LoggerFactory.getLogger(Sparql.class);

    public Sparql() {
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(new CssResourceReference(Sparql.class, "yasgui.min.css")));
        response.render(JavaScriptHeaderItem.forReference(new JavaScriptResourceReference(Sparql.class, "yasgui.min.js")));
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
    }
}
