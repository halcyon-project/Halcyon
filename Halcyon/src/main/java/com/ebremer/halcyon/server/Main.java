package com.ebremer.halcyon.server;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.services.ServicesLoader;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.filereaders.FileReaderFactoryProvider;
import com.ebremer.halcyon.imagebox.ImageServer;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.context.annotation.Lazy;
import com.ebremer.halcyon.fuseki.HalcyonProxyServlet;
import com.ebremer.halcyon.fuseki.SPARQLEndPoint;
import com.ebremer.halcyon.lib.spatial.Spatial;
import com.ebremer.halcyon.sparql.InvalidateSessionServlet;
import jakarta.annotation.PostConstruct;
import java.util.Iterator;
import javax.imageio.ImageIO;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.mitre.dsmiley.httpproxy.ProxyServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.pac4j.oidc.config.KeycloakOidcConfiguration;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketServletAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;

@SpringBootApplication(exclude = { WebSocketServletAutoConfiguration.class, LiquibaseAutoConfiguration.class, DataSourceAutoConfiguration.class })
@ConfigurationPropertiesScan({"com.ebremer.halcyon.server"})
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @Autowired
    private DefaultSslBundleRegistry defaultSslBundleRegistry;

    @PostConstruct
    public void init() {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    @Bean
    public KeycloakOidcConfiguration keycloakOidcConfiguration() {
        KeycloakOidcConfiguration config = new KeycloakOidcConfiguration();
        config.setClientId("account");
        config.setRealm("Halcyon");
        config.setBaseUri(HalcyonSettings.getSettings().getProxyHostName());
        //config.setBaseUri(HalcyonSettings.getSettings().getProxyHostName());
        if (HalcyonSettings.getSettings().isHTTPS2enabled()) {
            config.setSslSocketFactory(defaultSslBundleRegistry.getBundle("server").createSslContext().getSocketFactory());
        }
        return config;
    }

    @Lazy(false)
    @Bean
    ServletRegistrationBean ImageServerRegistration() {
        ServletRegistrationBean srb = new ServletRegistrationBean();
        srb.setLoadOnStartup(3);
        srb.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        srb.setServlet(new ImageServer());
        srb.setUrlMappings(Arrays.asList("/iiif/*"));
        return srb;
    }

    @Lazy(true)
    @Bean
    ServletRegistrationBean RaptorServerRegistration() {
        ServletRegistrationBean srb = new ServletRegistrationBean();
        srb.setLoadOnStartup(3);
        srb.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
        srb.setServlet(new Raptor());
        srb.setUrlMappings(Arrays.asList("/raptor/*"));
        return srb;
    }

    @Lazy(true)
    @Bean
    ServletRegistrationBean InvalidateSessionRegistration() {
        ServletRegistrationBean srb = new ServletRegistrationBean();
        srb.setLoadOnStartup(3);
        srb.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        srb.setServlet(new InvalidateSessionServlet());
        srb.setUrlMappings(Arrays.asList("/invalidateSession/*"));
        return srb;
    }

    @Bean
    public ServletRegistrationBean proxyServletRegistrationBean() {
        HalcyonSettings settings = HalcyonSettings.getSettings();
        ServletRegistrationBean bean = new ServletRegistrationBean(new HalcyonProxyServlet(), "/rdf/*");
        bean.addInitParameter("targetUri", "http://localhost:" + settings.GetSPARQLPort() + "/rdf");
        bean.addInitParameter(ProxyServlet.P_PRESERVECOOKIES, "true");
        bean.addInitParameter(ProxyServlet.P_HANDLEREDIRECTS, "true");
        bean.setOrder(5);
        return bean;
    }

    @Bean
    public ServletRegistrationBean proxyServletKeycloakRegistrationBean() {
        ServletRegistrationBean bean = new ServletRegistrationBean(new HalcyonProxyServlet(), "/auth/*");
        bean.addInitParameter("targetUri", "http://localhost:8080/auth");
        //bean.addInitParameter("targetUri", "https://ebremer.com/auth");
        bean.addInitParameter(ProxyServlet.P_PRESERVECOOKIES, "true");
        bean.addInitParameter(ProxyServlet.P_HANDLEREDIRECTS, "true");
        bean.addInitParameter(ProxyServlet.P_FORWARDEDFOR, "false");
        bean.addInitParameter(ProxyServlet.P_PRESERVEHOST, "true");
        bean.addInitParameter(ProxyServlet.P_LOG, "true");
        bean.setOrder(5);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<CustomFilter> KeycloakOIDCFilterFilterRegistration() {
        FilterRegistrationBean<CustomFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CustomFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    public static void main(String[] args) {
        logger.info("Starting Halcyon...");
        Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName("tif");
        readers.forEachRemaining(ir->{
            System.out.println("TIF READER LOADED : "+ir.getClass().toGenericString());
        });
        INIT i = new INIT();
        i.init();
        DataCore dc = DataCore.getInstance();
        Dataset ds = dc.getDataset();        
        ds.begin(ReadWrite.WRITE);
        ds.removeNamedModel("https://localhost:8888/ldp/utah/HnE/Stack2/stack.jsonld");
        //Stack stack = new Stack();
        ds.removeNamedModel("https://localhost:8888/utah/HnE/Stack2/stack.jsonld");
//        ds.removeNamedModel("file:///D:/HalcyonStorage/utah/HnE/Stack2/stack.jsonld");
        //ds.addNamedModel("https://localhost:8888/stack", stack.getModel());
        ds.commit();
        ds.end();
        if (!(System.getProperty("spring.aot.processing") != null)) {
            SPARQLEndPoint.getSPARQLEndPoint();
        }    
        ServicesLoader.init();
        FileReaderFactoryProvider.init(Main.class.getClassLoader());
        //Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName("tif");
        //readers.forEachRemaining(rr -> {
          //  System.out.println("MAIN LOAD TIF READERS : " + rr.getClass().toGenericString());
        //});

        Spatial.init();
        SpringApplicationBuilder sab = new SpringApplicationBuilder(Main.class);
       // sab.initializers(new ServletInitializer());
        SpringApplication app = sab.build();
        //SpringApplication app = new SpringApplication(Main.class);
        app.setMainApplicationClass(Main.class);
        app.addInitializers(new ServletInitializer());
        app.setAdditionalProfiles("production");
        app.setBannerMode(Mode.CONSOLE);
        app.run(args);
        System.out.println("===================== Welcome to Halcyon!");
    }
}
