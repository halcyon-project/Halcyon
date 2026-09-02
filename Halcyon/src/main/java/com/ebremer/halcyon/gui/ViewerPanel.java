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
        // C5: the <script> element comes from the markup now and carries the request's
        // nonce (CspNonce); this Label supplies only its BODY. It previously built the
        // whole "<script>…</script>" string here, which Wicket renders verbatim — the
        // nonce decorator only stamps tags Wicket itself renders, so that script had
        // none and script-src blocked it, leaving the viewer blank.
        //
        // setEscapeModelStrings(false) is still required: the body contains quotes, and
        // a <script> element's content is raw text, so an escaped &#039; would be a JS
        // syntax error rather than an apostrophe. Safe because every value interpolated
        // below is an int.
        add(new Label("inlineScript",
                "pageSetup('contentDiv', images, "+(numx*numy)+", "+numx+", "+numy+", "+w+", "+h+", options);")
                .setEscapeModelStrings(false)
                .add(new CspNonce()));
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
