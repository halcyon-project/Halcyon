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
import jakarta.annotation.PostConstruct;
import jakarta.servlet.Servlet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLSocketFactory;
//import javax.sql.DataSource;
import org.keycloak.federation.sssd.SSSDFederationProviderFactory;
import org.mitre.dsmiley.httpproxy.ProxyServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.pac4j.oidc.client.KeycloakOidcClient;
import org.pac4j.oidc.config.KeycloakOidcConfiguration;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.builder.SpringApplicationBuilder;
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
    private KeycloakOidcConfiguration keycloakOidcConfiguration;
        
    @Autowired
    public Main(KeycloakServer properties) {
        this.properties = properties;
        SSSDFederationProviderFactory ha;
        KeycloakProperties.getInstance(properties.getContextPath(), properties.getUsername(), properties.getPassword());
    }

    //@Autowired
    //private DataSource dataSource;
    
    @PostConstruct
    public void init() {
        // Remove existing handlers attached to j.u.l root logger
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        // Add SLF4JBridgeHandler to j.u.l's root logger
        SLF4JBridgeHandler.install();
    }
    
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
        if (HalcyonSettings.getSettings().isHTTPS2enabled()) {
            SSLSocketFactory sf = SslConfig.getSslContext().getSocketFactory();
            config.setSslSocketFactory(sf);
        }
        return config;
    }
    
    @Bean
    public KeycloakOidcClient keycloakOidcClient() {
        return new KeycloakOidcClient(keycloakOidcConfiguration);
    }

    @Bean("fixedThreadPool")
    @Order(Ordered.HIGHEST_PRECEDENCE)	
    ExecutorService fixedThreadPool() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
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
        INIT i = new INIT();
        i.init();
        DataCore.getInstance();
        SPARQLEndPoint.getSPARQLEndPoint();
        ServicesLoader halcyonServiceLoader = new ServicesLoader();
        ClassLoader loader = Main.class.getClassLoader();
        FileReaderFactoryProvider frf = new FileReaderFactoryProvider(loader);
        Spatial.init();
        SpringApplicationBuilder sab = new SpringApplicationBuilder(Main.class);
        sab.initializers(new ServletInitializer());
        SpringApplication app = sab.build();
        //SpringApplication app = new SpringApplication(Main.class);        
        app.setAdditionalProfiles("production");
        app.setBannerMode(Mode.CONSOLE);
        app.run(args);
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
