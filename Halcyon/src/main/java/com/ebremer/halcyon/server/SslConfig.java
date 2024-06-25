package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    
    //@Bean
    //public SSLContext sslContext() throws Exception {
      //  return getSslContext();
    //}
    
    public static SSLContext getSslContext() {
        if (HalcyonSettings.getSettings().isHTTPS2enabled()) {
            String keyStorePassword = "password";
            String trustStorePassword = "password";
            KeyStore keyStore;
            try {
                keyStore = KeyStore.getInstance("JKS");
            } catch (KeyStoreException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
            try (FileInputStream keyStoreInput = new FileInputStream("halcyonkeystore.jks")) {
                keyStore.load(keyStoreInput, keyStorePassword.toCharArray());
            } catch (FileNotFoundException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            } catch (IOException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            } catch (CertificateException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
            KeyStore trustStore;
            try {
                trustStore = KeyStore.getInstance("JKS");
            } catch (KeyStoreException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
            try (FileInputStream trustStoreInput = new FileInputStream("halcyontruststore.jks")) {
                trustStore.load(trustStoreInput, trustStorePassword.toCharArray());
            } catch (FileNotFoundException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
            } catch (CertificateException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
            }
            KeyManagerFactory keyManagerFactory;
            try {
                keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
            try {
                keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());
            } catch (KeyStoreException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
            } catch (UnrecoverableKeyException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
            }
            TrustManagerFactory trustManagerFactory;
            try {
                trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
            try {
                trustManagerFactory.init(trustStore);
            } catch (KeyStoreException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
            SSLContext sslContext;
            try {
                sslContext = SSLContext.getInstance("TLSv1.3");
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
            try {
                sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
            } catch (KeyManagementException ex) {
                Logger.getLogger(SslConfig.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
            return sslContext;
        }
        return null;
    }
}
