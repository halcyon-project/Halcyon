package com.ebremer.halcyon.keycloak;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;

/**
 *
 * @author erich
 */
public class KeycloakOIDCFilterConfig implements FilterConfig {
    private final String name;
    private final HashMap<String,String> params;
    private ServletContext context;
            
    public KeycloakOIDCFilterConfig(String name) {
        this.name = name;
        params = new HashMap<>();
    }

    @Override
    public String getFilterName() {
        return name;
    }

    @Override
    public ServletContext getServletContext() {
        return context;
    }
    
    public HashMap<String,String> getParams() {
        return params;
    }
    
    public void setContext(ServletContext context) {
        this.context = context;
    }
    
    @Override
    public String getInitParameter(String name) {
        return params.get(name);
    }

    public KeycloakOIDCFilterConfig setInitParameter(String name, String value) {
        params.put(name, value);
        return this;
    }
    
    @Override
    public Enumeration<String> getInitParameterNames() {
        return Collections.enumeration(params.keySet());
    }   
}
