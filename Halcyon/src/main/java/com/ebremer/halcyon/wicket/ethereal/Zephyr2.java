package com.ebremer.halcyon.wicket.ethereal;

import com.ebremer.halcyon.wicket.JsSafe;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.wicket.BasePage;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;

/**
 *
 * @author erich
 */
public class Zephyr2 extends BasePage {

    private static final long serialVersionUID = 102163948377788566L;
    private String options;

    // Default constructor required by Wicket
    public Zephyr2() {
        // Initialization can be deferred
    }

    public Zephyr2(String target) {
        // target = IIIF URI to the image
        this.options = "const options = {target: '" + target + "'}";
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        if (options != null) {
            response.render(JavaScriptHeaderItem.forScript(options, "options"));
        }
        HalcyonSession hs = HalcyonSession.get();
        HalcyonPrincipal hp = hs.getHalcyonPrincipal();
        // C5: the raw Keycloak access token is no longer published to the DOM —
        // the /rdf proxy attaches it server-side from the session. The remaining
        // JWT-derived values go through JsSafe instead of being concatenated
        // between quotes inside an inline <script>.
        response.render(JavaScriptHeaderItem.forScript(
                "var useriri = " + JsSafe.jsString(hp.getUserURI())
                + "; var userName = " + JsSafe.jsString(hp.getPreferredUserName()) + ";", "token"));
    }
}
