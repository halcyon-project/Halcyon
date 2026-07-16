package com.ebremer.halcyon.wicket;

import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.request.mapper.parameter.PageParameters;

public class Upload extends BasePage {
    private static final long serialVersionUID = 1L;
    private final String path;
        
    public Upload(final PageParameters parameters) {
        String frag;
        if (parameters.contains("container")) {
            frag = parameters.get("container").toString();
        } else {
            frag = "/ldp/lostandfound";
        }
        path = "const path = '"+frag+"';";
    }

    @Override
    public void renderHead(IHeaderResponse response) {
	super.renderHead(response);        
        response.render(JavaScriptHeaderItem.forScript(path, "path"));
    }
}
