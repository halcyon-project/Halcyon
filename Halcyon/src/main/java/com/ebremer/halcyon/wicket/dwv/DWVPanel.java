package com.ebremer.halcyon.wicket.dwv;

import com.ebremer.halcyon.gui.CspNonce;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.panel.Panel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class DWVPanel extends Panel {
    private static final Logger logger = LoggerFactory.getLogger(DWVPanel.class);
    private static final long serialVersionUID = 102163948377788566L;
    
    public DWVPanel(String id, String x) {
        super(id);
        logger.debug("{}", x+ "is inside");
    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
	super.renderHead(response);
        response.render(JavaScriptHeaderItem.forReference(DWVJavaScriptResourceReference.get()));
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
        add(new WebMarkupContainer("cspDwv").add(new CspNonce()));
    }
}
