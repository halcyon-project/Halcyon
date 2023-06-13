package com.ebremer.multiviewer;

import com.ebremer.halcyon.gui.ViewerPanel;
import com.ebremer.halcyon.wicket.BasePage;

public class MultiViewer extends BasePage {
    
    public MultiViewer() {
        ViewerPanel vp = new ViewerPanel("viewer1");
        add(vp);
    }
}
