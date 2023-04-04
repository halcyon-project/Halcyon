package com.ebremer.halcyon.sparql;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.wicket.BasePage;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.request.resource.CssResourceReference;
import org.apache.wicket.request.resource.JavaScriptResourceReference;

/**
 *
 * @author erich
 */

//reference https://triply.cc/docs/yasgui-api

public class Sparql extends BasePage {
    
    public Sparql() {
        add(new FeedbackPanel("feedback"));
    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(new CssResourceReference(Sparql.class, "yasgui.min.css")));
        response.render(JavaScriptHeaderItem.forReference(new JavaScriptResourceReference(Sparql.class, "yasgui.min.js")));
  //      HalcyonSettings s = HalcyonSettings.getSettings();
        //response.render(JavaScriptHeaderItem.forScript("var host = '"+s.getProxyHostName()+":"+s.GetSPARQLPort()+"'", "hostme"));
//        HttpServletRequest request = ((HttpServletRequest) getRequest().getContainerRequest());
        HalcyonSession hs = HalcyonSession.get();
        HalcyonPrincipal hp = hs.getHalcyonPrincipal();
        String token = hp.getToken();
        response.render(JavaScriptHeaderItem.forScript("var token = '"+token+"'", "token"));
    }
}
