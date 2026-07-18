package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.Blank;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.gui.LogoutLink;
import org.apache.wicket.markup.head.CssReferenceHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.request.resource.CssResourceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class MenuPanel extends Panel {
    private static final Logger logger = LoggerFactory.getLogger(MenuPanel.class);
    
    public MenuPanel(String id) {
        super(id);
        HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
        String host = HalcyonSettings.getSettings().getProxyHostName();
        add(new ExternalLink("home", host+"/","Home"));
        add(new ExternalLink("about", host+"/about","About"));
        ExternalLink images = new ExternalLink("images", host+"/ListImages","Images");
        ExternalLink security = new ExternalLink("security", host+"/admin","Settings");
        ExternalLink stacks = new ExternalLink("stacks", host+"/stacks","Stacks");
        ExternalLink sparql = new ExternalLink("sparql", host+"/sparql","SPARQL");
        ExternalLink account = new ExternalLink("account", host+"/user/account","Account");
        ExternalLink colorclasses = new ExternalLink("colorclasses", host+"/user/colorclasses","Color Classes");
        ExternalLink threed = new ExternalLink("threed", host+"/threed","3D");
        ExternalLink storage = new ExternalLink("storage", host+"/storage","Storage");
        ExternalLink lwscontainers = new ExternalLink("lwscontainers", host+"/lwscontainers","LWS Containers");
        ExternalLink revisionhistory = new ExternalLink("revisionhistory", host+"/revisionhistory","Revision History");
        //ExternalLink login = new ExternalLink("loginLink", host+"/gui/login","Login");
        Link login = new Link<Void>("loginLink") {
            @Override
            public void onClick() {
                getSession().invalidate();
                setResponsePage(Blank.class);
            }
        };
        LogoutLink logout = new LogoutLink("logoutLink");
        add(images);
        add(account);
        add(colorclasses);
        add(security);
        add(sparql);
        add(stacks);
        add(storage);
        add(lwscontainers);
        add(threed);
        add(logout);
        add(login);
        add(revisionhistory);
        images.setVisible(false);
        security.setVisible(false);
        threed.setVisible(false);
        account.setVisible(false);
        colorclasses.setVisible(false);
        storage.setVisible(false);
        lwscontainers.setVisible(false);
        sparql.setVisible(false);
        stacks.setVisible(false);
        logout.setVisible(false);
        login.setVisible(false);
        revisionhistory.setVisible(false);
        if (hp.isAnon()) {
            login.setVisible(true);
        } else {
            revisionhistory.setVisible(true);
            login.setVisible(false);
            logout.setVisible(true);
            // The account page shows the user THEIR OWN data — every signed-in
            // user gets it, not only admins.
            account.setVisible(true);
            images.setVisible(true);
            sparql.setVisible(true);
            stacks.setVisible(true);
            // Visible to every signed-in user. The page shows only what ACP
            // permits them, so gating it by role here would be redundant.
            storage.setVisible(true);
            lwscontainers.setVisible(true);
            hp.getGroups().forEach(k->{
                logger.debug("GROUP : {}", k);
            });
            if (hp.getGroups().contains("admin")) {
                security.setVisible(true);
                //threed.setVisible(true);
                colorclasses.setVisible(true);
            }
        } 
    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
	super.renderHead(response);
        response.render(CssReferenceHeaderItem.forReference(new CssResourceReference(getClass(), "MenuPanel.css")));
    }
}
