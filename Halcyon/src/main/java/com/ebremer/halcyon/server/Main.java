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
import com.ebremer.halcyon.lib.OperatingSystemInfo;
import com.ebremer.halcyon.lib.spatial.Spatial;
import com.ebremer.halcyon.server.ldp.LDPServer;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.Servlet;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import org.jboss.resteasy.core.ServerResponseWriter;
import org.mitre.dsmiley.httpproxy.ProxyServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.pac4j.oidc.client.KeycloakOidcClient;
import org.pac4j.oidc.config.KeycloakOidcConfiguration;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

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
        
    @Autowired
    public Main(KeycloakServer properties) {
        this.properties = properties;
        KeycloakProperties.getInstance(properties.getContextPath(), properties.getUsername(), properties.getPassword());
    }

    @Autowired
    private DataSource dataSource;
    
    @PostConstruct
    public void init() {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
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
    
    @Bean
    MultipartResolver multipartResolver() {
        ServerResponseWriter ha;
        return new StandardServletMultipartResolver();
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
            config.setSslSocketFactory(defaultSslBundleRegistry.getBundle("server").createSslContext().getSocketFactory());
        }
        return config;
    }
    
    @Bean
    public KeycloakOidcClient keycloakOidcClient() {
        return new KeycloakOidcClient(keycloakOidcConfiguration);
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
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        logger.info("Starting Halcyon...");
        INIT i = new INIT();
        i.init();
        DataCore.getInstance();
        SPARQLEndPoint.getSPARQLEndPoint();
        //ServicesLoader halcyonServiceLoader = new ServicesLoader();       
        ServicesLoader.init();
        FileReaderFactoryProvider.init(Main.class.getClassLoader());
        Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName("tif");
        readers.forEachRemaining(rr->{
            System.out.println("MAIN LOAD TIF READERS : "+rr.getClass().toGenericString());
        });
        
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
                if (OperatingSystemInfo.ifWindows()) {
                    srb.addInitParameter("resourceBase", rh.resourceBase().getPath().substring(1));
                    System.out.println("Add Path --> "+rh.urlPath()+"  "+rh.resourceBase().getPath().substring(1));
                } else {
                    srb.addInitParameter("resourceBase", rh.resourceBase().getPath());
                    System.out.println("Add Path --> "+rh.urlPath()+"  "+rh.resourceBase().getPath());
                }
                srb.addInitParameter("dirAllowed", "true");
                srb.setServlet(new LDPServer());
                srb.setUrlMappings(Arrays.asList(rh.urlPath()+"*"));
                applicationContext.getBeanFactory().registerSingleton(name, srb);
            });
            ServletRegistrationBean<Servlet> srb = new ServletRegistrationBean();
                srb.setLoadOnStartup(3);
                String name = "LDP "+UUID.randomUUID().toString();
                srb.setBeanName(name);
                srb.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
                srb.addInitParameter("resourceBase", "D:/HalcyonStorage/users/");
                srb.addInitParameter("dirAllowed", "true");
                srb.setServlet(new LDPServer());
                srb.setUrlMappings(Arrays.asList("/users/*"));
                applicationContext.getBeanFactory().registerSingleton(name, srb);
        }
    }    
}
