package com.ebremer.multiviewer;

import com.ebremer.halcyon.gui.ViewerPanel;
import com.ebremer.halcyon.wicket.BasePage;
import org.apache.wicket.markup.head.IHeaderResponse;

public class MultiViewer extends BasePage {
    private static final long serialVersionUID = 102163948377788566L;
    
    public MultiViewer() {
        ViewerPanel vp = new ViewerPanel("viewer1");
        add(vp);
    }

    @Override
    public void renderHead(IHeaderResponse response) {
	super.renderHead(response);
    }
}
