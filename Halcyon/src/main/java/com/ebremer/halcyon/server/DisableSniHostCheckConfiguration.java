package com.ebremer.halcyon.server;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.ServerConnector;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author erich
 */
@Configuration
public class DisableSniHostCheckConfiguration {

    @Bean
    WebServerFactoryCustomizer<JettyServletWebServerFactory> disableSniHostCheck() {
        return (factory) -> {
            factory.addServerCustomizers((server) -> {
                for (Connector connector : server.getConnectors()) {
                    if (connector instanceof ServerConnector serverConnector) {
                        HttpConnectionFactory connectionFactory = serverConnector.getConnectionFactory(HttpConnectionFactory.class);
                        if (connectionFactory != null) {
                            SecureRequestCustomizer secureRequestCustomizer = connectionFactory.getHttpConfiguration()
                                .getCustomizer(SecureRequestCustomizer.class);
                            if (secureRequestCustomizer != null) {
                                secureRequestCustomizer.setSniHostCheck(false);
                            }
                        }
                    }
                }
            });
        };
    }
}