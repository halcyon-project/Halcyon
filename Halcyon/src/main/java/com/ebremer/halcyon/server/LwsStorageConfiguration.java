package com.ebremer.halcyon.server;

import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.http.LwsServlet;
import jakarta.servlet.ServletRegistration;
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
            for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
                ServletRegistration.Dynamic reg =
                        servletContext.addServlet("LWS " + cfg.urlPath(), new LwsServlet(cfg));
                if (reg == null) {
                    // Name already taken — a duplicate :hasLWSStorage urlPath.
                    LOG.error("LWS storage {} not mounted: a servlet with that name already exists",
                            cfg.urlPath());
                    continue;
                }
                reg.addMapping(cfg.servletMapping());
                reg.setLoadOnStartup(3);
                reg.setAsyncSupported(true);
                LOG.info("mounted W3C LWS storage at {} -> {} ({} naming)",
                        cfg.servletMapping(), cfg.contentRoot(), cfg.naming());
            }
        };
    }
}
