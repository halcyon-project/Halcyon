package com.ebremer.halcyon.server;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.services.ServicesLoader;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.filereaders.FileReaderFactoryProvider;
import com.ebremer.halcyon.imagebox.ImageServer;
import com.ebremer.halcyon.server.keycloak.RequestFilter;
import com.ebremer.halcyon.server.keycloak.providers.SimplePlatformProvider;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import javax.sql.DataSource;
import org.jboss.resteasy.plugins.server.servlet.HttpServlet30Dispatcher;
import org.jboss.resteasy.plugins.server.servlet.ResteasyContextParameters;
import org.keycloak.platform.Platform;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import com.ebremer.halcyon.fuseki.HalcyonProxyServlet;
import com.ebremer.halcyon.fuseki.SPARQLEndPoint;
import com.ebremer.halcyon.lib.spatial.Spatial;
import com.ebremer.halcyon.server.ldp.LDP;
import jakarta.servlet.Servlet;
import java.util.UUID;
import org.mitre.dsmiley.httpproxy.ProxyServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.pac4j.oidc.client.KeycloakOidcClient;
import org.pac4j.oidc.config.KeycloakOidcConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(exclude = LiquibaseAutoConfiguration.class)
@Import( {KeycloakServer.class})
@EnableConfigurationProperties(KeycloakServer.class)
@ConfigurationPropertiesScan({ "com.ebremer.halcyon.server"})
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private final KeycloakServer properties;
    
    @Autowired
    private DefaultSslBundleRegistry defaultSslBundleRegistry;
    
    @Autowired
    private KeycloakOidcConfiguration keycloakOidcConfiguration;
        
    public Main(KeycloakServer properties) {
        this.properties = properties;
        KeycloakProperties.getInstance(properties.getContextPath(), properties.getUsername(), properties.getPassword());
    }

    @Autowired
    private DataSource dataSource;
    
    @Bean(name = "xapp")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Lazy(false)
    @DependsOn("keycloakServer")
    ServletRegistrationBean<HttpServlet30Dispatcher> keycloakJaxRsApplication() throws Exception {
        System.out.println("Add Keycloak Server Filter..."+properties);
        final var servlet = new ServletRegistrationBean<HttpServlet30Dispatcher>(new HttpServlet30Dispatcher());
        servlet.addInitParameter("jakarta.ws.rs.Application", App.class.getName());
        servlet.addInitParameter(ResteasyContextParameters.RESTEASY_SERVLET_MAPPING_PREFIX, properties.getContextPath());
        servlet.addInitParameter(ResteasyContextParameters.RESTEASY_USE_CONTAINER_FORM_PARAMS, "true");
        servlet.addUrlMappings(properties.getContextPath()+ "/*");
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
        filter.addUrlPatterns(properties.getContextPath() + "/*");
        return filter;
    }
    
    @Bean
    public KeycloakOidcConfiguration keycloakOidcConfiguration() {
        KeycloakOidcConfiguration config = new KeycloakOidcConfiguration();
        config.setClientId("account");
        config.setRealm("Halcyon");
        config.setBaseUri(HalcyonSettings.getSettings().getProxyHostName()+"/auth");  
        SSLContext sc = defaultSslBundleRegistry.getBundle("server").createSslContext();
        config.setSslSocketFactory(sc.getSocketFactory());
        return config;
    }
    
    @Bean
    public KeycloakOidcClient keycloakOidcClient() {
        return new KeycloakOidcClient(keycloakOidcConfiguration);
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

    @Lazy(true)
    @Bean
    ServletRegistrationBean ImageServerRegistration () {
        ServletRegistrationBean srb = new ServletRegistrationBean();
        srb.setLoadOnStartup(3);
        srb.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        srb.setServlet(new ImageServer());
        srb.setUrlMappings(Arrays.asList("/iiif/*"));
        return srb;
    }
    
    @Lazy(true)
    @Bean
    ServletRegistrationBean RaptorServerRegistration () {
        ServletRegistrationBean srb = new ServletRegistrationBean();
        srb.setLoadOnStartup(3);
        srb.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
        srb.setServlet(new Raptor());
        srb.setUrlMappings(Arrays.asList("/raptor/*"));
        return srb;
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
    
    @Bean
    public FilterRegistrationBean<CustomFilter> KeycloakOIDCFilterFilterRegistration(){
        FilterRegistrationBean<CustomFilter> registration = new FilterRegistrationBean<>();
	registration.setFilter(new CustomFilter());
        registration.addUrlPatterns("/ldp/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        logger.info("Starting Halcyon...");
        //if (HalcyonSettings.getSettings().isHTTPS2enabled()) {
            logger.info("Setting Key and Trust stores...");
            System.setProperty("javax.net.ssl.keyStore", "halcyonkeystore.jks");
            System.setProperty("javax.net.ssl.keyStorePassword", "password");
            System.setProperty("javax.net.ssl.trustStore", "halcyontruststore.jks");
            System.setProperty("javax.net.ssl.trustStorePassword", "password");
        //}
        DataCore.getInstance();
        SPARQLEndPoint.getSPARQLEndPoint();
        ServicesLoader halcyonServiceLoader = new ServicesLoader();
        ClassLoader loader = Main.class.getClassLoader();
        FileReaderFactoryProvider frf = new FileReaderFactoryProvider(loader);
        INIT i = new INIT();
        i.init();
        Spatial.init();
        SpringApplicationBuilder sab = new SpringApplicationBuilder(Main.class);
        sab.initializers(new ServletInitializer());
        SpringApplication app = sab.build();
        //SpringApplication app = new SpringApplication(Main.class);        
        app.setAdditionalProfiles("production");
        app.setBannerMode(Mode.CONSOLE);
        ApplicationContext yay = app.run(args);
        System.out.println("===================== Welcome to Halcyon!");
    }
    
    static class ServletInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            HalcyonSettings.getSettings().GetResourceHandlers().forEach(rh->{
                ServletRegistrationBean<Servlet> srb = new ServletRegistrationBean();
                srb.setLoadOnStartup(3);
                String name = "LDP "+UUID.randomUUID().toString();
                srb.setBeanName(name);
                srb.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
                srb.addInitParameter("resourceBase", rh.resourceBase().getPath().substring(1));
                srb.addInitParameter("dirAllowed", "true");
                System.out.println("Add Path --> "+rh.urlPath()+"  "+rh.resourceBase().getPath().substring(1));
                srb.setServlet(new LDP());
                srb.setUrlMappings(Arrays.asList(rh.urlPath()+"*"));
                applicationContext.getBeanFactory().registerSingleton(name, srb);
            });
        }
    }    
}

    /*
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
    }*/

    
    /*
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public FilterRegistrationBean<HALKeycloakOIDCFilter> KeycloakOIDCFilterFilterRegistration(){
        FilterRegistrationBean<HALKeycloakOIDCFilter> registration = new FilterRegistrationBean<>();
        registration.setName("keycloak");
	registration.setFilter(new HALKeycloakOIDCFilter());
	registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addInitParameter(KeycloakOIDCFilter.CONFIG_FILE_PARAM, "keycloak.json");        
        registration.addInitParameter(KeycloakOIDCFilter.SKIP_PATTERN_PARAM, "(^/zephyr.*|^/h2.*|^/skunkworks/.*|^/puffin.*|^/threejs/.*|^/halcyon.*|^/zephyrx.*|^/rdf.*|^/talon/.*|/;jsessionid=.*|/gui/images/halcyon.png|^/wicket/resource/.*|^/multi-viewer.*|^/iiif.*|^/|^/about|^/ListImages.*|^/wicket/resource/com.*\\.css||^/auth/.*|^/favicon.ico)");
        registration.setEnabled(true);
        return registration;
    }*/


/*    
    private static class TrustAllHostnames implements HostnameVerifier {
        @Override
        public boolean verify(String s, SSLSession sslSession) {
            return true; // Trust all hostnames
        }
    }

    private static void trustAllHosts() {    
        TrustAllHostnames trust = new TrustAllHostnames();
        HttpsURLConnection.setDefaultHostnameVerifier(trust);
    }
    
    private static void trustAllCertificates() {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                
                @Override
                public void checkClientTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
                }
                
                @Override
                public void checkServerTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
                }
            }
        };

        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            System.out.println("Exception occurred while setting up all-trusting trust manager:" + e.getMessage());
        }
    }*/
    
    
    /*
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)	
    public FilterRegistrationBean<SimpleLoggingFilter> loggingFilter() {
        FilterRegistrationBean<SimpleLoggingFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new SimpleLoggingFilter());
        registrationBean.addUrlPatterns("/*");
        return registrationBean;
    }*/
    
    /*
    @Component
    public class CustomTomcatConfiguration implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

        @Override
        public void customize(TomcatServletWebServerFactory factory) {
            HalcyonSettings settings = HalcyonSettings.getSettings();
            factory.setPort(settings.GetHTTPPort());
            
            //Ssl ssl = new Ssl();
            //ssl.setKeyStore("classpath:keystore.jks"); // Key store file in the classpath
            //ssl.setKeyStorePassword("yourKeystorePassword"); // Key store password
            //ssl.setKeyAlias("yourKeyAlias"); // Key alias in the key store

            //factory.setSsl(ssl);
            //factory.setProtocol(Http11NioProtocol.class.getName());
            //factory.setPort(settings.GetHTTPSPort());
            //factory.addAdditionalTomcatConnectors(createStandardConnector());
            
        }

        private Connector createStandardConnector() {
            HalcyonSettings settings = HalcyonSettings.getSettings();
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(settings.GetHTTPPort());
            return connector;
        }
    }*/

    /*
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
    }*/

    /*
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
    }*/