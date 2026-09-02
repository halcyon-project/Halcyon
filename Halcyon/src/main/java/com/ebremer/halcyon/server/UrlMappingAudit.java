package com.ebremer.halcyon.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Refuses to start if a URL pattern this server relies on cannot do its job.
 *
 * <p>Checked at the end of singleton initialisation — after every registration bean exists, before
 * the connector accepts traffic — so a mapping that guards nothing aborts the boot instead of
 * becoming a silent hole. See {@link UrlPatternRules} for why silence was the whole problem: a
 * filter pattern that Jetty cannot match produces no error, no warning, and no log line, so a
 * security filter registered on {@code "/iiif*&#47;"} was indistinguishable from one that was working.
 *
 * <p><strong>Deliberately not {@code @Conditional}.</strong> Every bean in {@code Cool} is
 * {@code @Conditional(KeycloakEnabled.class)}, and the checked-in {@code settings.ttl} declares no
 * {@code :AuthServer} — so a check hosted alongside the filters would never run in the configuration
 * the project actually ships. The pattern lists in {@link URLControl} are wrong whether or not a
 * filter is built from them, and this says so on every boot.
 */
@Configuration
public class UrlMappingAudit {

    private static final Logger LOG = LoggerFactory.getLogger(UrlMappingAudit.class);

    /**
     * Named distinctly from the enclosing class on purpose: a {@code @Configuration} class is itself
     * registered under its decapitalised simple name, so a {@code @Bean} method called
     * {@code urlMappingAudit} inside {@code UrlMappingAudit} is a second definition of that name and
     * the context refuses to start. No test in this repository boots the application context, so
     * that collision reached a real launch.
     */
    @Bean
    static SmartInitializingSingleton securityUrlPatternAudit(
            ObjectProvider<AbstractFilterRegistrationBean<?>> filters,
            ObjectProvider<ServletRegistrationBean<?>> servlets) {
        return () -> {
            // The declared lists first, so they are checked even when no filter is built from them.
            // The lists this project maintains get the strict rule: they must be fit to GUARD.
            List<String> secured = Arrays.asList(URLControl.getSecuredURLs());
            UrlPatternRules.assertGuardable("URLControl.getSecuredURLs()", secured);
            UrlPatternRules.assertGuardable("URLControl.getAdminURLs()",
                    Arrays.asList(URLControl.getAdminURLs()));

            // Then whatever actually got registered, including filters from other modules.
            // Registrations get form-legality only. Many are not ours -- Spring's dispatcherServlet
            // is mapped on "/" and Wicket's filter on "/*", both correct -- so the "must not cover
            // everything" rule above would reject the framework's own wiring.
            List<String> servletMappings = new ArrayList<>();
            filters.stream()
                    .filter(AbstractFilterRegistrationBean::isEnabled)
                    .forEach(f -> UrlPatternRules.assertLegal(
                            "filter registration " + describe(f.getFilterName()), f.getUrlPatterns()));
            servlets.stream()
                    .filter(ServletRegistrationBean::isEnabled)
                    .forEach(s -> {
                        UrlPatternRules.assertLegal(
                                "servlet registration " + describe(s.getServletName()),
                                s.getUrlMappings());
                        servletMappings.addAll(s.getUrlMappings());
                    });

            // And the half that legality alone cannot catch: a pattern that claims a servlet's URL
            // space without covering it.
            UrlPatternRules.assertCovers("URLControl.getSecuredURLs()", secured, servletMappings);

            LOG.info("URL mapping audit passed: {} secured pattern(s) over {} servlet mapping(s)",
                    secured.size(), servletMappings.size());
        };
    }

    private static String describe(String name) {
        return name == null || name.isBlank() ? "(unnamed)" : "\"" + name + "\"";
    }
}
