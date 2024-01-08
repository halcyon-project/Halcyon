package com.ebremer.halcyon.server;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConfigurationProperties( prefix = "keycloak.server")
@Primary
public class KeycloakServer {
    private String contextPath;
    private String username;
    private String password;

    public String getContextPath() {
        return contextPath;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
    
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
