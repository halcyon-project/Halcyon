package com.ebremer.halcyon.fuseki;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.keycloak.KeycloakSecurityContext;
import org.mitre.dsmiley.httpproxy.ProxyServlet;

/**
 *
 * @author erich
 */
public class HalcyonProxyServlet extends ProxyServlet {
    
    private KeycloakSecurityContext getKeycloakSecurityContext(HttpServletRequest request) {
        return (KeycloakSecurityContext) request.getAttribute(KeycloakSecurityContext.class.getName());
    } 
    
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        super.service(request, response);
        /*
        KeycloakSecurityContext securityContext = getKeycloakSecurityContext(request);
        String token = securityContext.getIdTokenString();
        HashMap<String,String> list = new HashMap<>();
        list.put("token", token);
        System.out.println("======================================================================");
        System.out.println(token);
        System.out.println("======================================================================");
*/
        //super.service(new AddParamsToHeader(request), response);
    }
}
