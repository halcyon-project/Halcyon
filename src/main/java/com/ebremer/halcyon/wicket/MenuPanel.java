package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.HalcyonSettings;
import org.apache.wicket.markup.head.CssReferenceHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.request.resource.CssResourceReference;

/**
 *
 * @author erich
 */
public class MenuPanel extends Panel {
    
    public MenuPanel(String id) {
        super(id);
        String host = HalcyonSettings.getSettings().getProxyHostName();
        add(new ExternalLink("home", host+"/gui","Home"));
        add(new ExternalLink("collections", host+"/gui/collections","Collections"));
        //add(new ExternalLink("addcollection", host+"/gui/add/collection","Add Collection"));
        add(new ExternalLink("images", host+"/gui/ListImages","Images"));
        add(new ExternalLink("sparql", host+"/gui/sparql","SPARQL"));
        //add(new ExternalLink("features", host+"/gui/ListFeatures","Features"));
        //add(new ExternalLink("viewer", host+"/gui/viewer","Viewer"));
        add(new ExternalLink("threed", host+"/gui/threed","3D"));
        add(new ExternalLink("security", host+"/gui/adminme","Security"));
        add(new ExternalLink("account", host+"/gui/accountpage","Account"));
        add(new ExternalLink("about", host+"/gui/about","About"));
        //add(new ExternalLink("logout", host+"/auth/realms/master/protocol/openid-connect/logout?redirect_uri="+host+"/","Logout"));
        add(new ExternalLink("logout", host+"/auth/realms/master/protocol/openid-connect/logout","Logout"));
    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
	super.renderHead(response);
        response.render(CssReferenceHeaderItem.forReference(new CssResourceReference(getClass(), "MenuPanel.css")));
    }
}