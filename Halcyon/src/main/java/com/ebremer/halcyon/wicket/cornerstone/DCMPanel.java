package com.ebremer.halcyon.wicket.cornerstone;

import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.panel.Panel;

/**
 *
 * @author erich
 */
public class DCMPanel extends Panel {
    private static final long serialVersionUID = 102163948377788566L;
    
    public DCMPanel(String id, String x) {
        super(id);
        System.out.println(x+ "is inside");
    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
	super.renderHead(response);
        response.render(JavaScriptHeaderItem.forReference(DCMJavaScriptResourceReference.get()));
        response.render(JavaScriptHeaderItem.forReference(example.get()));
    }
    
}
