package com.ebremer.halcyon.gui;

import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.panel.Panel;

/**
 *
 * @author erich
 */
public class ViewerPanel extends Panel {
    private static final String options = "const options = {filterOn: true, toolbarOn: true, paintbrushColor: '#0ff', viewerOpts: {showFullPageControl: true,showHomeControl: true,showZoomControl: true}}";
    
    public ViewerPanel(String id) {
        super(id);
    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
	super.renderHead(response);
        response.render(JavaScriptHeaderItem.forScript(HalcyonSession.get().getMV(), "images"));
        response.render(JavaScriptHeaderItem.forScript(options, "options"));
    }   
}
