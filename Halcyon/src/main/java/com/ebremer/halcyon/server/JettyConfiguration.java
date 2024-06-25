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
import javax.net.ssl.SSLContext;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class JettyConfiguration implements WebServerFactoryCustomizer<JettyServletWebServerFactory> {
    private final SSLContext sslContext;
    //@Autowired
    //private DefaultSslBundleRegistry defaultSslBundleRegistry;

    //@Value("${server.port}")
    //private Integer serverPort = 8888;

    //@Value("${server.jetty.connection-idle-timeout}")
    private Integer idleTimeout = 900000;

   @Autowired 
    public JettyConfiguration(SSLContext sslContext) {
        this.sslContext = sslContext;        
    }
    
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
                httpConfig.setIdleTimeout(idleTimeout);
                HTTP3ServerConnector connector = new HTTP3ServerConnector(server, sslContextFactory, new HTTP3ServerConnectionFactory(httpConfig));
                connector.setHost(HalcyonSettings.getSettings().GetHostIP());
                connector.getQuicConfiguration().setPemWorkDirectory(Paths.get(System.getProperty("java.io.tmpdir")));
                connector.setPort(HalcyonSettings.getSettings().GetHTTPSPort());
                server.addConnector(connector);
            }*/
        };
        factory.addServerCustomizers(jettyServerCustomizer);
    }
}
