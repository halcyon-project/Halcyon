package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.capability.CapabilitySet;
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
 * so Spring finds it by component scan. (Historically that let these storages
 * coexist with the legacy {@code /lws/**} servlet without either knowing about
 * the other; the legacy servlet is now REMOVED and these are the data plane.)
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
            // One imaging bridge serves every storage: stateless beyond its trusted-key cache. As an
            // EndpointCapability it mounts the .iiif endpoint and advertises the IIIF Image service.
            LwsIiifBridge iiif = new LwsIiifBridge();
            // The store-wide SPARQL endpoint (LwsSparqlServlet, mapped at /rdf2 by Main). Advertised
            // in each storage's description as its SparqlService; built here so HalcyonLWS never
            // hardcodes an app-tier route. Only this core endpoint is advertised, not the
            // per-resource ?iri= / resource-URL query surface.
            String sparqlEndpoint = HalcyonSettings.getSettings().getProxyHostName() + "/rdf2";
            // The capabilities installed on each storage: the IIIF Image service (.iiif endpoint) and
            // the per-resource SPARQL endpoint (every BeakGraph resource queryable at its own URL, the
            // SERVICE federation surface — replacing the removed LwsResourceSparqlFilter). Both
            // stateless, so one set serves every storage; the module resolves and authorizes, the
            // capabilities only execute.
            CapabilitySet capabilities = CapabilitySet.of(iiif, new BeakGraphQueryCapability());
            List<String> mappings = new ArrayList<>();
            for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
                // Fail at boot, per storage: build the content store NOW, so a bad backend
                // declaration (an unknown :hasBackend, a misconfigured bucket) is one clear
                // log line and one unmounted storage - not an app that dies, and never a
                // servlet that lazily discovers the problem on its first request.
                final com.ebremer.lws.store.ContentStore content;
                try {
                    content = com.ebremer.lws.store.LwsStore.get().contentStore(cfg);
                } catch (RuntimeException e) {
                    LOG.error("LWS storage {} not mounted: {}", cfg.urlPath(), e.getMessage());
                    continue;
                }
                ServletRegistration.Dynamic reg =
                        servletContext.addServlet("LWS " + cfg.urlPath(),
                                new LwsServlet(cfg, sparqlEndpoint, capabilities));
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
                        cfg.servletMapping(), content, cfg.naming());
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
                // (Per-resource SPARQL used to be a servlet filter here; it is now the
                // BeakGraphQueryCapability installed on each LwsServlet above.)
            }
        };
    }
}
