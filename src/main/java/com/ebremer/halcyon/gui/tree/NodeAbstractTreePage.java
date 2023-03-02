package com.ebremer.halcyon.gui.tree;

import com.ebremer.halcyon.wicket.BasePage;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.request.resource.CssResourceReference;

public abstract class NodeAbstractTreePage extends BasePage {
    private static final long serialVersionUID = 1L;

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(new CssResourceReference(NodeAbstractTreePage.class, "tree.css")));
    }
}
