package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.gui.LogoutLink;
import com.ebremer.halcyon.sparql.Sparql;
import org.apache.wicket.markup.head.CssReferenceHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.request.resource.CssResourceReference;

/**
 *
 * @author erich
 */
public class MenuPanel extends Panel {
    
    public MenuPanel(String id) {
        super(id);
        HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
        String host = HalcyonSettings.getSettings().getProxyHostName();
        add(new ExternalLink("home", host+"/","Home"));
        add(new ExternalLink("images", host+"/ListImages","Images"));
        add(new ExternalLink("about", host+"/about","About"));
        ExternalLink security = new ExternalLink("security", host+"/admin","Security");
        ExternalLink sparql = new ExternalLink("sparql", host+"/sparql","SPARQL");
        ExternalLink account = new ExternalLink("account", host+"/user/account","Account");
        ExternalLink colorclasses = new ExternalLink("colorclasses", host+"/user/colorclasses","Color Classes");
        ExternalLink threed = new ExternalLink("threed", host+"/threed","3D");
        ExternalLink containers = new ExternalLink("containers", host+"/containers","Containers");
        ExternalLink revisionhistory = new ExternalLink("revisionhistory", host+"/revisionhistory","Revision History");
        //ExternalLink login = new ExternalLink("loginLink", host+"/gui/login","Login");
        Link login = new Link<Void>("loginLink") {
            @Override
            public void onClick() {
                getSession().invalidate();
                setResponsePage(Sparql.class);
            }
        };
        LogoutLink logout = new LogoutLink("logoutLink");
        add(account);
        add(colorclasses);
        add(security);
        add(sparql);
        add(containers);
        add(threed);
        add(logout);
        add(login);
        add(revisionhistory);
        security.setVisible(false);
        threed.setVisible(false);
        account.setVisible(false);
        colorclasses.setVisible(false);
        containers.setVisible(false);
        sparql.setVisible(false);
        logout.setVisible(false);
        login.setVisible(false);
        revisionhistory.setVisible(false);
        if (hp.isAnon()) {
            login.setVisible(true);
        } else {
            revisionhistory.setVisible(true);
            login.setVisible(false);
            logout.setVisible(true);
            //account.setVisible(true);
            sparql.setVisible(true);
            hp.getGroups().forEach(k->{
                System.out.println("GROUP : "+k);
            });
            if (hp.getGroups().contains("/admin")) {
                security.setVisible(true);
                //threed.setVisible(true);
                account.setVisible(true);
                colorclasses.setVisible(true);
                containers.setVisible(true);
            }
        } 
    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
	super.renderHead(response);
        response.render(CssReferenceHeaderItem.forReference(new CssResourceReference(getClass(), "MenuPanel.css")));
    }
}
