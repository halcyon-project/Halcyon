package com.ebremer.halcyon.server;

import jakarta.servlet.http.HttpServlet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots a real Spring context containing {@link UrlMappingAudit}.
 *
 * <p>This exists because the audit class shipped broken and every one of the ~29,000 tests in this
 * repository passed anyway. Nothing here starts an application context, so a defect in bean wiring
 * — as opposed to a defect in logic — has no way to fail the build, and the first thing to notice
 * was a developer launching the server:
 *
 * <pre>
 * The bean 'urlMappingAudit' ... could not be registered. A bean with that
 * name has already been defined ... and overriding is disabled.
 * </pre>
 *
 * <p>A {@code @Configuration} class is itself registered under its decapitalised simple name, so a
 * {@code @Bean} method sharing that name is a second definition of it. The rule is trivial once
 * seen and invisible until something refreshes a context, which is exactly what this does.
 *
 * <p>Deliberately not {@code @SpringBootTest}: that would start the whole application — Jetty, TDB2,
 * the LWS storages — and would be far too slow and too configuration-dependent to keep. Registering
 * the one configuration class is enough to catch the entire class of defect.
 */
class UrlMappingAuditContextTest {

    @Test
    void theAuditConfigurationRefreshesWithoutABeanNameCollision() {
        assertDoesNotThrow(() -> {
            try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
                ctx.register(UrlMappingAudit.class);
                ctx.refresh();
            }
        }, "UrlMappingAudit must be registrable in a Spring context");
    }

    /**
     * The configuration contributes exactly one initializer, and it runs. With no filter or servlet
     * registration beans present the audit still checks the declared pattern lists, so a refresh
     * that completes is also evidence the real patterns in {@link URLControl} pass.
     */
    @Test
    void theAuditRunsAndTheShippedPatternsPass() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(UrlMappingAudit.class);
            ctx.refresh();
            // Name the bean rather than count the type: Spring's own EventListenerMethodProcessor
            // also implements SmartInitializingSingleton, so a count here measures the framework's
            // infrastructure as much as ours.
            List<String> names = List.of(ctx.getBeanNamesForType(SmartInitializingSingleton.class));
            assertTrue(names.contains("securityUrlPatternAudit"),
                    "the audit initializer must be registered; found " + names);
            assertNotNull(ctx.getBean(UrlMappingAudit.class),
                    "the configuration class itself is a bean, which is why the @Bean method may "
                    + "not share its name");
        }
    }

    /**
     * The second failure this class exists for. Spring's own {@code dispatcherServlet} is mapped on
     * {@code "/"} — the default mapping, and entirely correct for a dispatcher. The audit applied
     * its secured-URL rule ("must not cover the whole application") to every registration it could
     * see, so it rejected the framework's wiring and the application would not start:
     *
     * <pre>
     * IllegalPatternException: servlet registration "dispatcherServlet":
     *   "/" is the default mapping and would cover the entire application
     * </pre>
     *
     * <p>Registrations are checked for FORM; only the lists this project maintains are checked for
     * fitness to guard.
     */
    @Test
    void aDispatcherServletOnTheDefaultMappingIsNotRejected() {
        assertDoesNotThrow(() -> {
            try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
                ctx.register(UrlMappingAudit.class, FrameworkRegistrations.class);
                ctx.refresh();
            }
        }, "the audit must tolerate the framework's own mappings");
    }

    /** The shapes a real boot presents: a dispatcher on "/" and a catch-all filter on "/*". */
    @Configuration
    static class FrameworkRegistrations {

        @Bean
        ServletRegistrationBean<HttpServlet> dispatcherServlet() {
            ServletRegistrationBean<HttpServlet> b =
                    new ServletRegistrationBean<>(new HttpServlet() { }, "/");
            b.setName("dispatcherServlet");
            return b;
        }

        @Bean
        ServletRegistrationBean<HttpServlet> imageServlet() {
            ServletRegistrationBean<HttpServlet> b =
                    new ServletRegistrationBean<>(new HttpServlet() { }, "/iiif/*");
            b.setName("imageServer");
            return b;
        }
    }
}
