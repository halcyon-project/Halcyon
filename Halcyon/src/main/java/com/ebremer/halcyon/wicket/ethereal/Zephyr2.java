package com.ebremer.halcyon.wicket.ethereal;

import com.ebremer.halcyon.wicket.BasePage;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;

/**
 *
 * @author erich
 */
public class Zephyr2 extends BasePage {
    private static final long serialVersionUID = 102163948377788566L;
    private final String options;
    
    public Zephyr2(String target) {
        this.options = "const options = {target: '"+target+"'}";
    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(JavaScriptHeaderItem.forScript(options, "options"));
    }
}
