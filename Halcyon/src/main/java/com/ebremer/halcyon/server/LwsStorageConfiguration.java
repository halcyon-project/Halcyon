package com.ebremer.halcyon.server;

import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.http.LwsServlet;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletRegistration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mounts the W3C Linked Web Storage servlets declared by {@code :hasLWSStorage} in
 * {@code settings.ttl}.
 *
 * <p>This is a plain {@code @Configuration} in the package {@code Main} lives in,
 * so Spring finds it by component scan. That is deliberate: neither {@code Main}
 * nor {@code ServletInitializer} — which mounts the legacy {@code /lws/**} servlet
 * — has to be touched. The two LWS implementations coexist without either knowing
 * about the other.
 *
 * <p>A {@link ServletContextInitializer} rather than a set of
 * {@code ServletRegistrationBean}s because the number of storages is decided at
 * runtime by the settings file. Spring Boot only collects
 * {@code ServletContextInitializer} beans <em>individually</em>, so a {@code @Bean}
 * returning a {@code List} of registrations would be a single bean of type
 * {@code List} and would silently never be applied.
 *
 * <p>If no storages are declared, nothing is registered and Halcyon boots exactly
 * as before. That matters for a fresh install: {@code INIT} writes a default
 * {@code settings.ttl} with no {@code :hasLWSStorage} in it.
 */
@Configuration
public class LwsStorageConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(LwsStorageConfiguration.class);

    /**
     * Registering the servlet is not by itself enough to make it reachable: the
     * Wicket filter is mapped on {@code /*} and runs before every servlet, so a
     * storage path Wicket claims would answer with the home page instead. The URL
     * trees are kept away from it by {@link URLControl#getWicketIgnores()}.
     */
    @Bean
    public ServletContextInitializer lwsStorageServlets() {
        return servletContext -> {
            // One imaging bridge serves every storage: it is stateless beyond its
            // trusted-key cache, and installing it is what makes each storage
            // advertise the IIIF Image service in its description.
            LwsIiifBridge iiif = new LwsIiifBridge();
            List<String> mappings = new ArrayList<>();
            for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
                ServletRegistration.Dynamic reg =
                        servletContext.addServlet("LWS " + cfg.urlPath(), new LwsServlet(cfg, iiif));
                if (reg == null) {
                    // Name already taken — a duplicate :hasLWSStorage urlPath.
                    LOG.error("LWS storage {} not mounted: a servlet with that name already exists",
                            cfg.urlPath());
                    continue;
                }
                reg.addMapping(cfg.servletMapping());
                reg.setLoadOnStartup(3);
                reg.setAsyncSupported(true);
                mappings.add(cfg.servletMapping());
                LOG.info("mounted W3C LWS storage at {} -> {} ({} naming)",
                        cfg.servletMapping(), cfg.contentRoot(), cfg.naming());
            }
            if (!mappings.isEmpty()) {
                // Session-pays-for-tiles: GET .iiif only, REQUEST + FORWARD (the
                // global /iiif servlet forwards LWS identifiers here). See the
                // filter's javadoc for why the scope is exactly this narrow.
                FilterRegistration.Dynamic f = servletContext.addFilter(
                        "LWS IIIF session auth", new LwsIiifSessionAuthFilter());
                if (f != null) {
                    f.addMappingForUrlPatterns(
                            EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD),
                            true, mappings.toArray(String[]::new));
                }
            }
        };
    }
}
