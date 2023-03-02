package com.ebremer.halcyon.gui;

import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.panel.Panel;

/**
 *
 * @author erich
 */
public class ViewerPanel extends Panel {
    private static final long serialVersionUID = 102163948377788366L;
    
    public ViewerPanel(String id) {
        super(id);
    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
	super.renderHead(response);
        HalcyonSession session = HalcyonSession.get();
        String images = session.getMV();
        String options="const options = {filterOn: true, toolbarOn: true, paintbrushColor: '#0ff', viewerOpts: {showFullPageControl: true,showHomeControl: true,showZoomControl: true}}";
        response.render(JavaScriptHeaderItem.forScript(images, "images"));
        response.render(JavaScriptHeaderItem.forScript(options, "options"));
    }   
}
