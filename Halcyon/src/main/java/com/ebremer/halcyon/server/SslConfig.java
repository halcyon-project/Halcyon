package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import java.io.FileInputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SslConfig {

    @Bean
    public WebServerFactoryCustomizer<JettyServletWebServerFactory> servletContainerCustomizer() {
        return factory -> {
            if (HalcyonSettings.getSettings().isHTTPS2enabled()) {
                Ssl ssl = new Ssl();
                ssl.setKeyStore("halcyonkeystore.jks");
                ssl.setKeyStorePassword("password");
                ssl.setKeyStoreType("JKS");
                ssl.setKeyAlias("halcyon");
                ssl.setTrustStore("halcyontruststore.jks");
                ssl.setTrustStorePassword("password");
                ssl.setTrustStoreType("JKS");
                ssl.setEnabledProtocols(new String[]{"TLSv1.3"});
                factory.setSsl(ssl);
            }
        };
    }
    
    @Bean
    public SSLContext sslContext() throws Exception {
        return getSslContext();
    }
    
    public static SSLContext getSslContext() throws Exception {
        String keyStorePassword = "password";
        String trustStorePassword = "password";
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream keyStoreInput = new FileInputStream("halcyonkeystore.jks")) {
            keyStore.load(keyStoreInput, keyStorePassword.toCharArray());
        }
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream trustStoreInput = new FileInputStream("halcyontruststore.jks")) {
            trustStore.load(trustStoreInput, trustStorePassword.toCharArray());
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }
}
