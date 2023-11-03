package com.ebremer.halcyon.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 *
 * @author erich
 */

@Configuration
public class AppConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
        
    /*
    @Bean
    public KeycloakLogoutHandler keycloakLogoutHandler(RestTemplate restTemplate) {
        return new KeycloakLogoutHandler(restTemplate);
    }*/
    
}
