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

    @Bean
    static SmartInitializingSingleton urlMappingAudit(
            ObjectProvider<AbstractFilterRegistrationBean<?>> filters,
            ObjectProvider<ServletRegistrationBean<?>> servlets) {
        return () -> {
            // The declared lists first, so they are checked even when no filter is built from them.
            List<String> secured = Arrays.asList(URLControl.getSecuredURLs());
            UrlPatternRules.assertLegal("URLControl.getSecuredURLs()", secured);
            UrlPatternRules.assertLegal("URLControl.getAdminURLs()",
                    Arrays.asList(URLControl.getAdminURLs()));

            // Then whatever actually got registered, including filters from other modules.
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
