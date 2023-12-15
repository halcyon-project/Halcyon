package com.ebremer.halcyon.server;

import com.ebremer.halcyon.services.ServicesLoader;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.fuseki.HalcyonProxyServlet;
import com.ebremer.halcyon.datum.SessionsManager;
import com.ebremer.halcyon.filereaders.FileReaderFactoryProvider;
import com.ebremer.halcyon.imagebox.ImageServer;
import com.ebremer.halcyon.keycloak.HALKeycloakOIDCFilter;
import com.ebremer.halcyon.server.keycloak.App;
import com.ebremer.halcyon.server.keycloak.RequestFilter;
import com.ebremer.halcyon.server.keycloak.ServerProperties;
import com.ebremer.halcyon.server.keycloak.providers.SimplePlatformProvider;
import io.undertow.UndertowOptions;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.naming.CompositeName;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NameParser;
import javax.naming.NamingException;
import javax.naming.spi.NamingManager;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.plugins.server.servlet.HttpServlet30Dispatcher;
import org.jboss.resteasy.plugins.server.servlet.ResteasyContextParameters;
import org.keycloak.adapters.servlet.KeycloakOIDCFilter;
import org.keycloak.adapters.spi.SessionIdMapper;
import org.keycloak.platform.Platform;
import org.mitre.dsmiley.httpproxy.ProxyServlet;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;

@Slf4j
@SpringBootApplication(exclude = LiquibaseAutoConfiguration.class)
@EnableConfigurationProperties(ServerProperties.class)
@RequiredArgsConstructor
public class Main {
    private static final SessionIdMapper sessionidmapper = SessionsManager.getSessionsManager().getSessionIdMapper();    
    private final ServerProperties properties;
    private final DataSource dataSource;

    
    @Bean(name = "xapp")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Lazy(false)
    ServletRegistrationBean<HttpServlet30Dispatcher> keycloakJaxRsApplication() throws Exception {
        System.out.println("Add Keycloak Server Filter...");
        mockJndiEnvironment();
        final var servlet = new ServletRegistrationBean<HttpServlet30Dispatcher>(new HttpServlet30Dispatcher());
        servlet.addInitParameter("javax.ws.rs.Application", App.class.getName());
        servlet.addInitParameter(ResteasyContextParameters.RESTEASY_SERVLET_MAPPING_PREFIX, properties.contextPath());
        servlet.addInitParameter(ResteasyContextParameters.RESTEASY_USE_CONTAINER_FORM_PARAMS, "true");
        servlet.addUrlMappings(properties.contextPath() + "/*");
        servlet.setLoadOnStartup(0);
        servlet.setAsyncSupported(true);
        return servlet;
    }

    @Bean(name = "keycloakSessionManagement")
    @Order(Ordered.HIGHEST_PRECEDENCE)	
    FilterRegistrationBean<RequestFilter> keycloakSessionManagement() {
        System.out.println("Add Keycloak Session Management Filter...");
        final var filter = new FilterRegistrationBean<RequestFilter>();
        filter.setName("Keycloak Session Management");
        filter.setOrder(0);
        filter.setFilter(new RequestFilter());
        filter.addUrlPatterns(properties.contextPath() + "/*");
        return filter;
    }

    private void mockJndiEnvironment() throws NamingException {
        NamingManager.setInitialContextFactoryBuilder((env) -> (environment) -> new InitialContext() {
            @Override
            public Object lookup(Name name) {
                return lookup(name.toString());
            }
                
            @Override
            public Object lookup(String name) {
                if ("spring/datasource".equals(name)) {
                    return dataSource;
                } else if (name.startsWith("java:jboss/ee/concurrency/executor/")) {
                    return fixedThreadPool();
                }
                return null;
            }

            @Override
            public NameParser getNameParser(String name) {
                return CompositeName::new;
            }

            @Override
            public void close() {}
        });
    }

    @Bean("fixedThreadPool")
    @Order(Ordered.HIGHEST_PRECEDENCE)	
    ExecutorService fixedThreadPool() {
        return Executors.newFixedThreadPool(5);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)	
    @ConditionalOnMissingBean(name = "springBootPlatform")
    protected SimplePlatformProvider springBootPlatform() {
        return (SimplePlatformProvider) Platform.getPlatform();
    }       

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public UndertowServletWebServerFactory embeddedServletContainerFactory() {
        System.out.println("Configuring Undertow Web Engine...");
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
        int cores = Runtime.getRuntime().availableProcessors();
        int ioThreads = cores;
        int taskThreads = 16*cores;
        System.out.println("ioThreads   : "+ioThreads+"\ntaskThreads : "+taskThreads);
        factory.setIoThreads(ioThreads);
        HalcyonSettings settings = HalcyonSettings.getSettings();
        factory.setPort(settings.GetHTTPPort());
        if (settings.isHTTPSenabled()) {
            factory.addBuilderCustomizers(builder -> {
                SSLContext ssl;
                try {
                    ssl = getSSLContext();
                    System.out.println("HTTPS PORT : "+settings.GetHTTPSPort());
                    builder
                        .addHttpsListener(settings.GetHTTPSPort(), settings.GetHostIP(), ssl)
                        .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                        .setServerOption(UndertowOptions.MAX_PARAMETERS, 100000)
                        .setServerOption(UndertowOptions.MAX_CONCURRENT_REQUESTS_PER_CONNECTION, 100);
                } catch (Exception ex) {
                    log.error(ex.toString());
                }
            });
        } else {
            System.out.println("HTTPS not enabled.");
        }
        return factory;
    }
        
    @Lazy(true)
    @Bean
    ServletRegistrationBean ImageServerRegistration () {
        ServletRegistrationBean srb = new ServletRegistrationBean();
        srb.setLoadOnStartup(3);
        srb.setServlet(new ImageServer());
        srb.setUrlMappings(Arrays.asList("/iiif/*"));
        return srb;
    }
        
    
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public FilterRegistrationBean<HALKeycloakOIDCFilter> KeycloakOIDCFilterFilterRegistration(){
        FilterRegistrationBean<HALKeycloakOIDCFilter> registration = new FilterRegistrationBean<>();
        registration.setName("keycloak");
	registration.setFilter(new HALKeycloakOIDCFilter());
	registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addInitParameter(KeycloakOIDCFilter.CONFIG_FILE_PARAM, "keycloak.json");        
        registration.addInitParameter(KeycloakOIDCFilter.SKIP_PATTERN_PARAM, "(^/h2.*|^/skunkworks/.*|^/puffin.*|^/threejs/.*|^/halcyon.*|^/zephyr/.*|^/rdf.*|^/talon/.*|/;jsessionid=.*|/gui/images/halcyon.png|^/wicket/resource/.*|^/multi-viewer.*|^/iiif.*|^/|^/about|^/ListImages.*|^/wicket/resource/com.*\\.css||^/auth/.*|^/favicon.ico)");
        registration.setEnabled(true);
        return registration;
    }

    @Bean
    public ServletRegistrationBean proxyServletRegistrationBean() {
        HalcyonSettings settings = HalcyonSettings.getSettings();
        ServletRegistrationBean bean = new ServletRegistrationBean(new HalcyonProxyServlet(), "/rdf/*");
        bean.addInitParameter("targetUri", "http://localhost:"+settings.GetSPARQLPort()+"/rdf");
        bean.addInitParameter(ProxyServlet.P_PRESERVECOOKIES, "true");
        bean.addInitParameter(ProxyServlet.P_HANDLEREDIRECTS, "true");
        bean.setOrder(5);
        return bean;
    }
   

    private final String keyStorePassword = "changeit";
    private final String serverKeystore = "cacerts";
    private final String serverTruststore = "cacerts";
    private final String trustStorePassword = "changeit";
    
    public SSLContext getSSLContext() throws Exception {
        return createSSLContext(loadKeyStore(serverKeystore,keyStorePassword), loadKeyStore(serverTruststore,trustStorePassword));
    }

    private SSLContext createSSLContext(final KeyStore keyStore, final KeyStore trustStore) throws Exception {
        KeyManager[] keyManagers;
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());
        keyManagers = keyManagerFactory.getKeyManagers();
        TrustManager[] trustManagers;
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        trustManagers = trustManagerFactory.getTrustManagers();
        SSLContext sslContext;
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);
        return sslContext;
    }
    
    private static KeyStore loadKeyStore(final String storeLoc, final String storePw) throws Exception {
        InputStream stream = Files.newInputStream(Paths.get(storeLoc));
        if(stream == null) {
            throw new IllegalArgumentException("Could not load keystore");
        }
        try (InputStream is = stream) {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(is, storePw.toCharArray());
            return loadedKeystore;
        }
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        log.info("Starting Halcyon...");
        ServicesLoader halcyonServiceLoader = new ServicesLoader();
        ClassLoader loader = Main.class.getClassLoader();
        FileReaderFactoryProvider frf = new FileReaderFactoryProvider(loader);
        //FileReaderFactoryProvider.getReaderForFormat("svs");
        INIT i = new INIT();
        i.init();
        SpringApplication app = new SpringApplication(Main.class);
        app.setBannerMode(Mode.CONSOLE);
        ApplicationContext yay = app.run(args);
        System.out.println("===================== Welcome to Halcyon!");
    }
}
