package com.ebremer.multiviewer;

import com.ebremer.halcyon.gui.ViewerPanel;
import com.ebremer.halcyon.wicket.BasePage;

public class MultiViewer extends BasePage {
    
    public MultiViewer(int numx, int numy, int w, int h) {
        ViewerPanel vp = new ViewerPanel("viewer1", numx, numy, w, h);
        add(vp);
    }
}
