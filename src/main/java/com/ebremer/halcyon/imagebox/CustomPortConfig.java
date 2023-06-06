package com.ebremer.halcyon.imagebox;

import com.ebremer.halcyon.HalcyonSettings;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomPortConfig {
    /*
    private final HalcyonSettings settings = HalcyonSettings.getSettings();

    @Bean
    public WebServerFactoryCustomizer<UndertowServletWebServerFactory> webServerFactoryCustomizer() {
        return factory -> {
            factory.setPort(settings.GetHTTPPort());
            factory.set
        };
    }*/
}
