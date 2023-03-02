package com.ebremer.halcyon.wicket.ethereal;

import com.ebremer.halcyon.wicket.BasePage;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.request.resource.PackageResourceReference;

/**
 *
 * @author erich
 */
public class Zephyr extends BasePage {
    private static final long serialVersionUID = 102163948377788566L;
    
    public Zephyr() {

    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        //response.render(JavaScriptHeaderItem.forReference(new PackageResourceReference(getClass(),"three.js")));
       // response.render(JavaScriptHeaderItem.forReference(new PackageResourceReference(getClass(),"TrackballControls.js")));
        //response.render(JavaScriptHeaderItem.forReference(new PackageResourceReference(getClass(),"CSS2DRenderer.js")));
        //response.render(JavaScriptHeaderItem.forReference(new PackageResourceReference(getClass(),"3d-force-graph.min.js")));
        //response.render(JavaScriptHeaderItem.forReference(new PackageResourceReference(getClass(),"three-spritetext.min.js")));
       // response.render(JavaScriptHeaderItem.forReference(new PackageResourceReference(getClass(),"DataTextureLoader.js")));
    }
}
