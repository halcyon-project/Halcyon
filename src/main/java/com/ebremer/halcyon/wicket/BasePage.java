package com.ebremer.halcyon.wicket;

import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.WebPage;

/**
 *
 * @author erich
 */
public class BasePage extends WebPage {
    private static final long serialVersionUID = 1L;
    
    public BasePage() {
        add(new MenuPanel("menu"));
    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
    }
    
}
