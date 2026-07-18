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
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.ssl.DefaultSslBundleRegistry;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
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
        config.setClientId(HalcyonSettings.CLIENT_ID);
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

    // C2: the Raptor servlet registration is GONE. It ran QueryFactory.create()
    // on a raw request parameter against a path-selected BeakGraph with no
    // authentication, no WAC check and no LIMIT/timeout — anonymous read of
    // medical annotation data plus a trivial cartesian-product DoS. It had no
    // callers anywhere in the tree. If a query API is ever needed again it must
    // be authenticated, WAC-authorized and bounded, over the SECURED graph.

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

    @Lazy(true)
    @Bean
    ServletRegistrationBean SaveStackServletRegistration() {
        ServletRegistrationBean srb = new ServletRegistrationBean();
        srb.setLoadOnStartup(3);
        srb.setOrder(Ordered.HIGHEST_PRECEDENCE + 6);
        srb.setServlet(new SaveStackServlet());
        srb.setUrlMappings(Arrays.asList("/savestack"));
        return srb;
    }

    // /rdf2: read-only SPARQL over the LWS module's OWN TDB2, ACP-filtered per
    // request for the caller (see LwsSparqlServlet). Not a Fuseki mount on
    // purpose: a static dataset would freeze one agent's view forever, while
    // the ACP evaluator's contract is one instance per request.
    @Lazy(true)
    @Bean
    ServletRegistrationBean LwsSparqlServletRegistration() {
        ServletRegistrationBean srb = new ServletRegistrationBean();
        srb.setLoadOnStartup(3);
        srb.setOrder(Ordered.HIGHEST_PRECEDENCE + 7);
        srb.setServlet(new LwsSparqlServlet());
        srb.setUrlMappings(Arrays.asList("/rdf2", "/rdf2/*"));
        return srb;
    }

    @Bean
    public ServletRegistrationBean proxyServletRegistrationBean() {
        HalcyonSettings settings = HalcyonSettings.getSettings();
        // C5: `true` = attach the signed-in session's bearer token to the proxied
        // request server-side, so the page no longer has to publish it into
        // window.token for the browser to send. The /auth proxy below must stay
        // false (it talks to Keycloak itself).
        ServletRegistrationBean bean = new ServletRegistrationBean(new HalcyonProxyServlet(true), "/rdf/*");
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

    // C2: CustomFilter is GONE. Its only behaviour was to FORWARD-dispatch any
    // request carrying a ?query= parameter to /raptor, which bypassed the
    // REQUEST-dispatch pac4j security filter entirely (a FORWARD is not a
    // REQUEST). With the forward and the Raptor mount both removed there is
    // nothing left for it to do — every other path just called chain.doFilter.

    public static void main(String[] args) {
        logger.info("Starting Halcyon...");
        Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName("tif");
        readers.forEachRemaining(ir->{
            logger.debug("TIF READER LOADED : {}", ir.getClass().toGenericString());
        });
        INIT i = new INIT();
        i.init();
        DataCore dc = DataCore.getInstance();
        Dataset ds = dc.getDataset();        
        // H13: guarded WRITE. Startup path, so a strand here means the server comes
        // up unable to write anything rather than failing outright — which is worse
        // than crashing, because it looks healthy.
        ds.begin(ReadWrite.WRITE);
        try {
            ds.removeNamedModel("https://localhost:8888/ldp/utah/HnE/Stack2/stack.jsonld");
            //Stack stack = new Stack();
            ds.removeNamedModel("https://localhost:8888/utah/HnE/Stack2/stack.jsonld");
//            ds.removeNamedModel("file:///D:/HalcyonStorage/utah/HnE/Stack2/stack.jsonld");
            //ds.addNamedModel("https://localhost:8888/stack", stack.getModel());
            ds.commit();
        } catch (RuntimeException ex) {
            ds.abort();
            throw ex;
        } finally {
            ds.end();
        }
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
        logger.debug("===================== Welcome to Halcyon!");
    }
}
