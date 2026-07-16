package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.wicket.JsSafe;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;

/**
 *
 * @author erich
 */
public class ViewerPanel extends Panel {
    private static final String OPTIONS = "const options = {filterOn: true, toolbarOn: true, paintbrushColor: '#0ff', viewerOpts: {showFullPageControl: true, showHomeControl: true, showZoomControl: true, timeout: 60000}}";
    
    public ViewerPanel(String id, int numx, int numy, int w, int h) {
        super(id);
        add(new Label("inlineScript", "<script>pageSetup('contentDiv', images, "+(numx*numy)+", "+numx+", "+numy+", "+w+", "+h+", options);</script>").setEscapeModelStrings(false));
    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
	super.renderHead(response);
        // C5: getMV() is "var images = [ ...jakarta.json... ]" built in ListImages,
        // and those objects carry USER-AUTHORED colour-class names. jakarta.json
        // escapes quotes and backslashes but never '<' or '/', so a class named
        // "</script><script>…" terminated this inline <script> element and ran.
        // Harden the HTML-significant characters (they can only occur inside the
        // payload's string literals, where the escape decodes back to the original).
        response.render(JavaScriptHeaderItem.forScript(
                JsSafe.inlineScriptPayload(HalcyonSession.get().getMV()), "images"));
        response.render(JavaScriptHeaderItem.forScript(OPTIONS, "options"));
    }
}
