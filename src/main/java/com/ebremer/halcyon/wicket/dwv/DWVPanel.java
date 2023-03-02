package com.ebremer.halcyon.wicket.dwv;

import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.panel.Panel;

/**
 *
 * @author erich
 */
public class DWVPanel extends Panel {
    private static final long serialVersionUID = 102163948377788566L;
    
    public DWVPanel(String id, String x) {
        super(id);
        System.out.println(x+ "is inside");
    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
	super.renderHead(response);
        response.render(JavaScriptHeaderItem.forReference(DWVJavaScriptResourceReference.get()));
    }
}
