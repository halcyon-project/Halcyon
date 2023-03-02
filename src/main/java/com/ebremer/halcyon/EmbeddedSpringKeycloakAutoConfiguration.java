/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon;

import com.github.thomasdarimont.keycloak.embedded.EmbeddedKeycloakConfig;
import com.github.thomasdarimont.keycloak.embedded.KeycloakCustomProperties;
import com.github.thomasdarimont.keycloak.embedded.KeycloakProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({KeycloakProperties.class, KeycloakCustomProperties.class})
@ComponentScan(basePackageClasses = EmbeddedKeycloakConfig.class)
public class EmbeddedSpringKeycloakAutoConfiguration {
}