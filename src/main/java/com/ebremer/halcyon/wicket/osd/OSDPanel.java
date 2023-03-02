package com.ebremer.halcyon.wicket.osd;

import com.ebremer.halcyon.wicket.osd.references.OSDCssResourceReference;
import com.ebremer.halcyon.wicket.osd.references.OSDJavascriptResourceReference;
import com.ebremer.halcyon.wicket.osd.references.OpenSeadragonFilteringReference;
import com.ebremer.halcyon.wicket.osd.references.driverResourceReference;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.panel.Panel;

/**
 *
 * @author erich
 */
public class OSDPanel extends Panel {
    private static final long serialVersionUID = 102163948377788566L;
    
    public OSDPanel(String id, String x) {
        super(id);
        System.out.println(x+ "is inside");
    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
	super.renderHead(response);
        response.render(JavaScriptHeaderItem.forReference(OSDJavascriptResourceReference.get()));
	response.render(CssHeaderItem.forReference(OSDCssResourceReference.get()));
        response.render(JavaScriptHeaderItem.forReference(OpenSeadragonFilteringReference.get()));
        response.render(JavaScriptHeaderItem.forReference(driverResourceReference.get()));
    }
    
}
