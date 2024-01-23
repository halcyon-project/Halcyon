package com.ebremer.halcyon.server;

/**
 *
 * @author erich
 */
public class KeycloakProperties {
    private static KeycloakProperties instance;
    private final String contextPath;
    private final String username;
    private final String password;

    private KeycloakProperties(String contextPath, String username, String password) {
        this.contextPath = contextPath;
        this.username = username;
        this.password = password;
    }

    public static synchronized KeycloakProperties getInstance(String contextPath, String username, String password) {
        if (instance == null) {
            instance = new KeycloakProperties(contextPath, username, password);
        }
        return instance;
    }
    
    public static synchronized KeycloakProperties getInstance() {
        return instance;
    }

    public String getcontextPath() {
        return contextPath;
    }

    public String getusername() {
        return username;
    }

    public String getpassword() {
        return password;
    }
}
