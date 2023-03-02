/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon.gui;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.apache.wicket.request.cycle.RequestCycle;
import org.keycloak.KeycloakSecurityContext;

/**
 *
 * @author erich
 */

public class KeycloakTokenFilter implements ClientRequestFilter {

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        ServletWebRequest request = (ServletWebRequest) RequestCycle.get().getRequest();
        HttpServletRequest containerRequest = request.getContainerRequest();
        KeycloakSecurityContext securityContext = (KeycloakSecurityContext) containerRequest.getAttribute(KeycloakSecurityContext.class.getName());
        requestContext.getHeaders().add("Authorization", "Bearer "+ securityContext.getTokenString());	
    }
}


