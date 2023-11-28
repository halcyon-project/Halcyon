package com.ebremer.halcyon.server;

import java.util.logging.Level;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
//import org.springframework.security.authentication.AuthenticationManager;
//import static org.springframework.security.config.Customizer.withDefaults;
//import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
//import org.springframework.security.core.session.SessionRegistryImpl;
//import org.springframework.security.oauth2.jwt.JwtDecoder;
//import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
//import org.springframework.security.web.SecurityFilterChain;
//import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
//import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

/**
 *
 * @author erich
 */

//@Configuration	
//@EnableWebSecurity
public class SecurityConfig {} /*
           
    private final KeycloakLogoutHandler keycloakLogoutHandler;
        
    public SecurityConfig(KeycloakLogoutHandler keycloakLogoutHandler) {
        this.keycloakLogoutHandler = keycloakLogoutHandler;	
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        String issuerUri = "http://localhost:8888";
        return NimbusJwtDecoder.withJwkSetUri(issuerUri + "/.well-known/jwks.json").build();
    }    
        
    @Bean	
    protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {	
        return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());	
    }	
    
    @Order(10)
    @Bean	
    public SecurityFilterChain clientFilterChain(HttpSecurity http) {
        System.out.println("Starting SecurityFilterChain..........................................");
            try {
                http.authorizeRequests()
                    .antMatchers("/about/**").authenticated() // Require authentication for /securethisnow/**
                    .anyRequest().permitAll() // Allow all other requests
                    .and()
                    .oauth2Login()
                    .and()
                    .logout()
                    .addLogoutHandler(keycloakLogoutHandler)
                    .logoutSuccessUrl("/");
                return http.build();
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        return null;
    }	
    	
    @Order(20)	
    @Bean
    public SecurityFilterChain resourceServerFilterChain(HttpSecurity http) throws Exception {
        http.authorizeRequests()
            .antMatchers("/about**").hasRole("USER") // Require authentication
            .anyRequest().permitAll() // Allow all other requests
            .and()
            .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt);
        return http.build();
    }
        
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {	
        return http.getSharedObject(AuthenticationManagerBuilder.class).build();	
    }
    
    @Order(5)
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests((authz) -> authz
                .antMatchers("/xabout").authenticated() // Only authenticate requests to this
                .anyRequest().permitAll() // Permit all other requests
            )
            .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt); // Use OAuth2 for authentication

        return http.build();
    }    
}
*/