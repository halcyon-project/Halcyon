package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import org.eclipse.jetty.http3.server.HTTP3ServerConnectionFactory;
import org.eclipse.jetty.http3.server.HTTP3ServerConnector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.boot.web.embedded.jetty.JettyServerCustomizer;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;
import java.nio.file.Paths;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ServerConnector;
import org.springframework.beans.factory.annotation.Value;

/**
 *
 * @author erich
 */

@Configuration
public class JettyConfiguration implements WebServerFactoryCustomizer<JettyServletWebServerFactory> {
    
    @Autowired
    private DefaultSslBundleRegistry defaultSslBundleRegistry;

    @Value("${server.port}")
    private Integer serverPort;

    @Value("${server.jetty.connection-idle-timeout}")
    private Integer idleTimeout;
    
    @Override
    public void customize(JettyServletWebServerFactory factory) {
        System.setProperty("org.eclipse.jetty.server.Request.maxFormKeys", "2000");
        if (!HalcyonSettings.getSettings().isHTTPS2enabled()) {
            factory.setSsl(null);
        }
        JettyServerCustomizer jettyServerCustomizer = (JettyServerCustomizer) (Server server) -> {   
            //server.getMimeTypes().addMimeMapping("js", "application/javascript");
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
            /*
            if (HalcyonSettings.getSettings().isHTTPS3enabled()) {
                HttpConfiguration httpConfig = new HttpConfiguration();
                System.out.println("Setting up HTTP/3...");
                var keyStore = defaultSslBundleRegistry.getBundle("server").getStores().getKeyStore();
                SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
                sslContextFactory.setKeyStore(keyStore);
                sslContextFactory.setKeyStorePassword("");
                httpConfig.addCustomizer(new SecureRequestCustomizer());
                httpConfig.setIdleTimeout(idleTimeout);
                HTTP3ServerConnector connector = new HTTP3ServerConnector(server, sslContextFactory, new HTTP3ServerConnectionFactory(httpConfig));
                connector.setHost(HalcyonSettings.getSettings().GetHostIP());
                connector.getQuicConfiguration().setPemWorkDirectory( Paths.get(System.getProperty("java.io.tmpdir")) );
                connector.setPort(HalcyonSettings.getSettings().GetHTTPSPort());
                server.addConnector(connector);          
            }         */   
        };
        factory.addServerCustomizers(jettyServerCustomizer);
    }
}

/*
@Configuration
public class JettyConfiguration implements WebServerFactoryCustomizer<JettyServletWebServerFactory> {

    private final String keyStorePassword = "changeit";
    private final String serverKeystore = "cacerts";
    private final String serverTruststore = "cacerts";
    private final String trustStorePassword = "changeit";
    
    @Autowired
    private DefaultSslBundleRegistry defaultSslBundleRegistry;

    @Override
    public void customize(JettyServletWebServerFactory factory) {
        JettyServerCustomizer jettyServerCustomizer = (JettyServerCustomizer) (Server server) -> {
            HttpConfiguration httpConfig = new HttpConfiguration();
            ServerConnector httpConnector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
            httpConnector.setPort(HalcyonSettings.getSettings().GetHTTPPort());
            server.addConnector(httpConnector);
            if (HalcyonSettings.getSettings().isHTTPSenabled()) {
                var keyStore = defaultSslBundleRegistry.getBundle("server").getStores().getKeyStore();
                SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
                sslContextFactory.setKeyStore(keyStore);
                sslContextFactory.setKeyStorePassword("");
                httpConfig.addCustomizer(new SecureRequestCustomizer());
                //httpConfig.setIdleTimeout(idleTimeout);
                HTTP3ServerConnector connector = new HTTP3ServerConnector(server, sslContextFactory, new HTTP3ServerConnectionFactory(httpConfig));
                connector.getQuicConfiguration().setPemWorkDirectory( Paths.get(System.getProperty("java.io.tmpdir")) );
                connector.setPort(HalcyonSettings.getSettings().GetHTTPSPort());
                server.addConnector(connector);                
            }
        };
        factory.addServerCustomizers(jettyServerCustomizer);
    }
}
*/

/*
package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import org.eclipse.jetty.http3.server.HTTP3ServerConnectionFactory;
import org.eclipse.jetty.http3.server.HTTP3ServerConnector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.boot.web.embedded.jetty.JettyServerCustomizer;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;
import java.nio.file.Paths;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ServerConnector;

@Configuration
public class JettyConfiguration implements WebServerFactoryCustomizer<JettyServletWebServerFactory> {

    private final String keyStorePassword = "changeit";
    private final String serverKeystore = "cacerts";
    private final String serverTruststore = "cacerts";
    private final String trustStorePassword = "changeit";
    
    @Autowired
    private DefaultSslBundleRegistry defaultSslBundleRegistry;

    @Override
    public void customize(JettyServletWebServerFactory factory) {
        JettyServerCustomizer jettyServerCustomizer = (JettyServerCustomizer) (Server server) -> {
            HttpConfiguration httpConfig = new HttpConfiguration();
            ServerConnector httpConnector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
            httpConnector.setPort(HalcyonSettings.getSettings().GetHTTPPort());
            server.addConnector(httpConnector);
            if (HalcyonSettings.getSettings().isHTTPSenabled()) {
                SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
                sslContextFactory.setKeyStorePath(serverKeystore);
                sslContextFactory.setKeyStorePassword(keyStorePassword);
                sslContextFactory.setTrustStorePath(serverTruststore);
                sslContextFactory.setTrustStorePassword(trustStorePassword);

                httpConfig.addCustomizer(new SecureRequestCustomizer());
                HTTP3ServerConnector connector = new HTTP3ServerConnector(server, sslContextFactory, new HTTP3ServerConnectionFactory(httpConfig));
                connector.getQuicConfiguration().setPemWorkDirectory(Paths.get(System.getProperty("java.io.tmpdir")));
                connector.setPort(HalcyonSettings.getSettings().GetHTTPSPort());
                server.addConnector(connector);           
            }
        };
        factory.addServerCustomizers(jettyServerCustomizer);
    }
}
*/