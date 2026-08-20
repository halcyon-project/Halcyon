package com.ebremer.halcyon.server;

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
import org.springframework.context.annotation.Conditional;
import org.springframework.core.Ordered;
import org.springframework.context.annotation.Lazy;
import com.ebremer.halcyon.fuseki.HalcyonProxyServlet;
import com.ebremer.halcyon.lib.spatial.Spatial;
import com.ebremer.halcyon.sparql.InvalidateSessionServlet;
import jakarta.annotation.PostConstruct;
import java.util.Iterator;
import javax.imageio.ImageIO;
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

    static {
        // MCP-F3: pin the SLF4J binding to Logback deterministically, BEFORE the
        // logger field below triggers SLF4J initialization. BeakGraph ships as a
        // fat jar that embeds the log4j-slf4j2-impl classes AND their
        // META-INF/services SLF4J-provider registration — invisible to Maven
        // exclusions and the reactor's enforcer ban. With that provider on the
        // classpath next to Boot's log4j-to-slf4j, SLF4J picking it (which is
        // otherwise classpath-enumeration order — luck) makes log4j throw
        // "log4j-slf4j2-impl cannot be present with log4j-to-slf4j". Naming the
        // provider makes SLF4J use ONLY Logback and never instantiate the log4j
        // one, so the clash cannot arise. Honoured only when not already set, so
        // an explicit -Dslf4j.provider on the command line still wins.
        if (System.getProperty("slf4j.provider") == null) {
            System.setProperty("slf4j.provider", "ch.qos.logback.classic.spi.LogbackServiceProvider");
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @Autowired
    private DefaultSslBundleRegistry defaultSslBundleRegistry;

    @PostConstruct
    public void init() {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    // Keycloak-only: absent :AuthServer means this is never built. See KeycloakEnabled.
    @Bean
    @Conditional(KeycloakEnabled.class)
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

    // /colorclasses: the Zephyr palette's session-authenticated relay to the
    // user's LWS color-classes resource (with lazy migration off the old store).
    @Lazy(true)
    @Bean
    ServletRegistrationBean ColorClassesServletRegistration() {
        ServletRegistrationBean srb = new ServletRegistrationBean();
        srb.setLoadOnStartup(3);
        srb.setOrder(Ordered.HIGHEST_PRECEDENCE + 8);
        srb.setServlet(new ColorClassesServlet());
        srb.setUrlMappings(Arrays.asList("/colorclasses"));
        return srb;
    }

    // /webid-login + /webid-callback: interactive WebID login (Option B) — discovers the user's
    // OpenID Provider from a typed WebID and runs Authorization-Code + PKCE against it. Anonymous
    // (NOT in getSecuredURLs) and Wicket-ignored (URLControl). Off unless lws-oidc.json enables it;
    // the fixed-Keycloak login is untouched.
    @Lazy(true)
    @Bean
    ServletRegistrationBean WebIdLoginServletRegistration() {
        ServletRegistrationBean srb = new ServletRegistrationBean();
        srb.setLoadOnStartup(3);
        srb.setOrder(Ordered.HIGHEST_PRECEDENCE + 9);
        srb.setServlet(new WebIdLoginServlet());
        srb.setUrlMappings(Arrays.asList("/webid-login"));
        return srb;
    }

    @Lazy(true)
    @Bean
    ServletRegistrationBean WebIdCallbackServletRegistration() {
        ServletRegistrationBean srb = new ServletRegistrationBean();
        srb.setLoadOnStartup(3);
        srb.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        srb.setServlet(new WebIdCallbackServlet());
        srb.setUrlMappings(Arrays.asList("/webid-callback"));
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

    // The reverse proxy that puts Keycloak on this origin at /auth. With the subsystem
    // off there is nothing behind it, so the mount goes away with the rest of the stack
    // rather than answering 502 on a path nothing should be visiting.
    @Bean
    @Conditional(KeycloakEnabled.class)
    public ServletRegistrationBean proxyServletKeycloakRegistrationBean() {
        ServletRegistrationBean bean = new ServletRegistrationBean(new HalcyonProxyServlet(), "/auth/*");
        bean.addInitParameter("targetUri", "http://localhost:8080/auth");
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
        // Let SPARQL SERVICE calls to this server's own (self-signed) HTTPS origin complete their
        // TLS handshake — every LWS resource is a federatable SPARQL endpoint on that origin.
        ServiceHttpClient.install();
        ServicesLoader.init();
        FileReaderFactoryProvider.init(Main.class.getClassLoader());
        //Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName("tif");
        //readers.forEachRemaining(rr -> {
          //  System.out.println("MAIN LOAD TIF READERS : " + rr.getClass().toGenericString());
        //});

        Spatial.init();
        SpringApplicationBuilder sab = new SpringApplicationBuilder(Main.class);
        SpringApplication app = sab.build();
        //SpringApplication app = new SpringApplication(Main.class);
        app.setMainApplicationClass(Main.class);
        // The legacy LDP servlet (LWSServer, the old :hasResourceHandler mounts
        // and /users/*) is REMOVED — the W3C LWS storages are the data plane.
        app.setAdditionalProfiles("production");
        app.setBannerMode(Mode.CONSOLE);
        app.run(args);
        logger.debug("===================== Welcome to Halcyon!");
    }
}
