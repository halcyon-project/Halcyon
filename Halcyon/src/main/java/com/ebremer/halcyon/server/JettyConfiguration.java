package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import jakarta.servlet.MultipartConfigElement;
import java.util.Collection;
import org.eclipse.jetty.server.*;
import org.springframework.boot.jetty.servlet.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import org.springframework.boot.jetty.JettyServerCustomizer;

@Configuration
public class JettyConfiguration implements WebServerFactoryCustomizer<JettyServletWebServerFactory> {

    @Override
    public void customize(JettyServletWebServerFactory factory) {
        System.setProperty("org.eclipse.jetty.server.Request.maxFormKeys", "2000");

        factory.addServerCustomizers(new CustomJettyServerCustomizer());
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        // Use a proper temporary directory location; adjust as needed
        return new MultipartConfigElement("/tmp");
    }

    private static class CustomJettyServerCustomizer implements JettyServerCustomizer {
        @Override
        public void customize(Server server) {
            for (Connector connector : server.getConnectors()) {
                if (connector instanceof ServerConnector serverConnector) {
                    serverConnector.setHost(HalcyonSettings.getSettings().GetHostIP());
                    serverConnector.setPort(HalcyonSettings.getSettings().GetHTTPPort());

                    // Access HttpConnectionFactory directly
                    Collection<ConnectionFactory> connectionFactories = serverConnector.getConnectionFactories();
                    for (ConnectionFactory factory : connectionFactories) {
                        if (factory instanceof HttpConnectionFactory httpConnectionFactory) {
                            HttpConfiguration httpConfig = httpConnectionFactory.getHttpConfiguration();
                            List<HttpConfiguration.Customizer> customizers = httpConfig.getCustomizers();
                            for (HttpConfiguration.Customizer customizer : customizers) {
                                if (customizer instanceof SecureRequestCustomizer secureRequestCustomizer) {
                                    secureRequestCustomizer.setSniHostCheck(false);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/*
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import jakarta.servlet.MultipartConfigElement;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.springframework.boot.jetty.JettyServerCustomizer;
import org.springframework.boot.jetty.servlet.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ServerConnector;
import org.springframework.context.annotation.Bean;

@Configuration
public class JettyConfiguration implements WebServerFactoryCustomizer<JettyServletWebServerFactory> {
    
    @Override
    public void customize(JettyServletWebServerFactory factory) {
        System.setProperty("org.eclipse.jetty.server.Request.maxFormKeys", "2000");
        
        JettyServerCustomizer jettyServerCustomizer = (JettyServerCustomizer) (Server server) -> {
            for (Connector connector : server.getConnectors()) {
                if (connector instanceof ServerConnector serverConnector) {
                    serverConnector.setHost(HalcyonSettings.getSettings().GetHostIP());
                    serverConnector.setPort(HalcyonSettings.getSettings().GetHTTPPort());
                    HttpConnectionFactory connectionFactory = serverConnector.getConnectionFactory(HttpConnectionFactory.class);
                    if (connectionFactory != null) {
                        SecureRequestCustomizer secureRequestCustomizer = connectionFactory.getHttpConfiguration().getCustomizer(SecureRequestCustomizer.class);
                        if (secureRequestCustomizer != null) {
                            secureRequestCustomizer.setSniHostCheck(false);
                        }
                    }
                }
            }
        };
        factory.addServerCustomizers(jettyServerCustomizer);
    }

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        String ha = null;
        return new MultipartConfigElement(ha);
    }
}*/



            /*
            if (HalcyonSettings.getSettings().isHTTPS2enabled()) {
                HttpConfiguration httpConfig = new HttpConfiguration();
                httpConfig.addCustomizer(new SecureRequestCustomizer());

                SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
                sslContextFactory.setKeyStorePath("halcyonkeystore.jks");
                sslContextFactory.setKeyStorePassword("password");
                sslContextFactory.setKeyStoreType("JKS");
                sslContextFactory.setKeyManagerPassword("password");
                sslContextFactory.setCertAlias("halcyon");

                ServerConnector sslConnector = new ServerConnector(server, new SslConnectionFactory(sslContextFactory, "http/1.1"), new HttpConnectionFactory(httpConfig));
                sslConnector.setPort(HalcyonSettings.getSettings().GetHTTPSPort());
                server.addConnector(sslConnector);
            }*/

            /*
            if (HalcyonSettings.getSettings().isHTTPS3enabled()) {
                HttpConfiguration httpConfig = new HttpConfiguration();
                System.out.println("Setting up HTTP/3...");
              //  var keyStore = defaultSslBundleRegistry.getBundle("server").getStores().getKeyStore();
                SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
               // sslContextFactory.setKeyStore(keyStore);
                sslContextFactory.setKeyStorePassword("password");
                httpConfig.addCustomizer(new SecureRequestCustomizer());
                httpConfig.setIdleTimeout(900000);
                HTTP3ServerConnector connector = new HTTP3ServerConnector(server, sslContextFactory, new HTTP3ServerConnectionFactory(httpConfig));
                connector.setHost(HalcyonSettings.getSettings().GetHostIP());
                connector.getQuicConfiguration().setPemWorkDirectory(Paths.get(System.getProperty("java.io.tmpdir")));
                connector.setPort(HalcyonSettings.getSettings().GetHTTPSPort());
                server.addConnector(connector);
            }*/
            // Configure multipart support
//            ServletContextHandler context = new ServletContextHandler();
  //          context.setContextPath("/");
    //        context.setAttribute("org.eclipse.jetty.server.Request.maxFormContentSize", -1);
      //      context.setAttribute("org.eclipse.jetty.server.Request.maxFormKeys", -1);
        //    server.setHandler(context);   