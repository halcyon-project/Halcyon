package com.ebremer.halcyon.wicket.ethereal;

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
        response.render(JavaScriptHeaderItem.forScript("var token = '" + hp.getToken() + "'; var useriri = '" + hp.getUserURI() + "'; var userName = '" + hp.getPreferredUserName() + "';", "token"));
    }
}
