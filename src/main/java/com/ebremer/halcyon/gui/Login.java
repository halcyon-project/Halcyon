/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ebremer.halcyon.gui;

import javax.servlet.http.HttpServletRequest;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;
import org.keycloak.KeycloakSecurityContext;

/**
 *
 * @author erich
 */
public class Login extends WebPage implements IMarkupResourceStreamProvider {
    
    public Login(PageParameters parameters) {
         super(parameters);
         
         System.out.println("Login : "+parameters.getIndexedCount());
        add(new LoginForm("loginForm"));
        
        
                    ServletWebRequest request = (ServletWebRequest) RequestCycle.get().getRequest();
            HttpServletRequest containerRequest = request.getContainerRequest();
            KeycloakSecurityContext securityContext = (KeycloakSecurityContext) containerRequest.getAttribute(KeycloakSecurityContext.class.getName());
            System.out.println("===============");
            System.out.println(securityContext!=null);
            System.out.println("===============");
    }
    
    
    @Override
    public IResourceStream getMarkupResourceStream(MarkupContainer container, Class<?> containerClass) {
            return new StringResourceStream("<html><body>"
                    + "<form id=\"loginForm\" method=\"get\" wicket:id=\"loginForm\">\n" +
                      " <fieldset>\n" +
                      " <legend style=\"color: #F90\">Login</legend>\n" +
                      "<p wicket:id=\"loginStatus\"></p>\n" +
                      "<span>Username: </span><input wicket:id=\"username\" type=\"text\" id=\"username\" /><br/>\n" +
                      "<span>Password: </span><input wicket:id=\"password\" type=\"password\" id=\"password\" />\n" +
                      "<p>\n" +
                      "<input type=\"submit\" name=\"Login\" value=\"Login\"/>\n" +
                      "</p>\n" +
                      "</fieldset>\n" +
                    "</form></body></html>");
    }
    
}
