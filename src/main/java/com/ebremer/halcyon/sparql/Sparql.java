package com.ebremer.halcyon.sparql;

import com.ebremer.halcyon.HalcyonSettings;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.wicket.BasePage;
import io.undertow.server.session.SessionManager;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.SessionManagerFactory;
import io.undertow.servlet.handlers.ServletRequestContext;
import java.util.List;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import org.apache.wicket.Application;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.WebRequest;
import org.apache.wicket.request.resource.CssResourceReference;
import org.apache.wicket.request.resource.JavaScriptResourceReference;
import org.apache.wicket.session.HttpSessionStore;
import org.apache.wicket.session.ISessionStore;

/**
 *
 * @author erich
 */

//reference https://triply.cc/docs/yasgui-api

public class Sparql extends BasePage {
    
    public Sparql() {
        add(new FeedbackPanel("feedback"));

        //WebRequest webRequest = (WebRequest) RequestCycle.get().getRequest();
       
        /*
        List<Cookie> cookies = webRequest.getCookies();
        cookies.forEach(c->{
            System.out.println("Cookie: " + c.getName() + "=" + c.getValue());
        });
        */
        //HttpServletRequest req = ((HttpServletRequest) getRequest().getContainerRequest());
        //ServletContext servletContext = req.getServletContext();
        //DeploymentInfo deploymentInfo = ServletRequestContext.requireCurrent().getDeployment().getDeploymentInfo();
        //SessionManagerFactory sessionManagerFactory = deploymentInfo.getSessionManagerFactory();
        //System.out.println("Session Manager Factory: " + sessionManagerFactory);
        
        //SessionManager sm = sessionManagerFactory.createSessionManager(ServletRequestContext.requireCurrent().getDeployment());
       /*
        Set<String> sess = sm.getAllSessions();
        System.out.println(sess.size());
        sess.forEach(s->{
            System.out.println("SESSION --> "+s);
        });
        
        System.out.println("Wicket session store : "+Application.get().getSessionStore().getClass().toGenericString());
        ISessionStore iss = Application.get().getSessionStore();
        HttpSessionStore hss = (HttpSessionStore) iss;
        
*/

    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(new CssResourceReference(Sparql.class, "yasgui.min.css")));
        response.render(JavaScriptHeaderItem.forReference(new JavaScriptResourceReference(Sparql.class, "yasgui.min.js")));
  //      HalcyonSettings s = HalcyonSettings.getSettings();
        //response.render(JavaScriptHeaderItem.forScript("var host = '"+s.getProxyHostName()+":"+s.GetSPARQLPort()+"'", "hostme"));
//        HttpServletRequest request = ((HttpServletRequest) getRequest().getContainerRequest());
        response.render(JavaScriptHeaderItem.forScript("var token = '"+HalcyonSession.get().getToken()+"'", "token"));
    }
}
