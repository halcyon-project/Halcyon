package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 *
 * @author erich
 */
@Configuration
@Lazy(value = false)
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class HalcyonResourceConfiguration implements WebMvcConfigurer {
    private static final Logger logger = LoggerFactory.getLogger(HalcyonResourceConfiguration.class);
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String mv = HalcyonSettings.getSettings().getMultiewerLocation();
        if (mv!=null) {
            if (mv.startsWith("file:///")) {
                mv = mv.replace("file:///", "file:/");
            }
            registry.addResourceHandler("/multi-viewer/**").addResourceLocations(mv);
        } else {
            registry.addResourceHandler("/multi-viewer/**").addResourceLocations("classpath:/META-INF/public-web-resources/multi-viewer/");   
        }
        String talon = HalcyonSettings.getSettings().getTalonLocation();
        if (talon!=null) {
            if (talon.startsWith("file:///")) {
                talon = talon.replace("file:///", "file:/");
            }
            registry.addResourceHandler("/talon/**").addResourceLocations(talon);
        } else {
            registry.addResourceHandler("/talon/**").addResourceLocations("classpath:/META-INF/public-web-resources/talon/");
        }
        String zephyr = HalcyonSettings.getSettings().getZephyrLocation();
        // In dev mode the viewer modules are served live from source; without
        // an explicit Cache-Control the browser's heuristic caching keeps
        // serving stale copies after edits (a recurring dev-loop trap).
        // no-cache still allows conditional 304s, so unchanged files stay fast.
        CacheControl zephyrCache = HalcyonSettings.getSettings().isDevMode()
                ? CacheControl.noCache().mustRevalidate()
                : null;
        var zephyrHandler = registry.addResourceHandler("/zephyr/**");
        if (zephyr!=null) {
            if (zephyr.startsWith("file:///")) {
                zephyr = zephyr.replace("file:///", "file:/");
            }
            logger.info("Using Local Zephyr <"+zephyr+">");
            zephyrHandler.addResourceLocations(zephyr);
        } else {
            zephyrHandler.addResourceLocations("classpath:/META-INF/public-web-resources/zephyr/");
        }
        if (zephyrCache != null) {
            zephyrHandler.setCacheControl(zephyrCache);
        }
        registry.addResourceHandler("/threejs/**").addResourceLocations("classpath:/META-INF/public-web-resources/threejs/");
        registry.addResourceHandler("/images/**").addResourceLocations("classpath:/META-INF/public-web-resources/images/");
        registry.addResourceHandler("/rdflib/**").addResourceLocations("classpath:/META-INF/public-web-resources/rdflib/");
        registry.addResourceHandler("/favicon.ico").addResourceLocations("classpath:/META-INF/public-web-resources/favicon.ico");
        //registry.addResourceHandler("/HalcyonStorage/**").addResourceLocations("file:/D:/HalcyonStorage/");
    }
}
