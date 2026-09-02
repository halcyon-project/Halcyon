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
        // ALWAYS revalidate the viewer modules, not only in dev mode. They are
        // plain static files with no content hashing, so without an explicit
        // Cache-Control the browser's heuristic caching keeps serving stale
        // copies AFTER AN UPGRADE too — this exact trap kept serving a pre-H1
        // stackPersistence.js whose Save posted a SPARQL update to /rdf, which
        // died with a 400 once that endpoint went read-only, and no amount of
        // server rebuilding could fix it. no-cache still allows conditional
        // 304s, so unchanged files stay fast; it just forces revalidation.
        CacheControl zephyrCache = CacheControl.noCache().mustRevalidate();
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
        zephyrHandler.setCacheControl(zephyrCache);
        registry.addResourceHandler("/threejs/**").addResourceLocations("classpath:/META-INF/public-web-resources/threejs/");
        // L18: Graph3D's libraries, vendored instead of pulled from unpkg at runtime.
        // Separate from /threejs/ on purpose — that is three r160 for Zephyr, and
        // 3d-force-graph 1.80.0 requires three >=0.179.
        registry.addResourceHandler("/graph3d/**").addResourceLocations("classpath:/META-INF/public-web-resources/graph3d/");
        registry.addResourceHandler("/images/**").addResourceLocations("classpath:/META-INF/public-web-resources/images/");
        registry.addResourceHandler("/rdflib/**").addResourceLocations("classpath:/META-INF/public-web-resources/rdflib/");
        registry.addResourceHandler("/favicon.ico").addResourceLocations("classpath:/META-INF/public-web-resources/favicon.ico");
        //registry.addResourceHandler("/HalcyonStorage/**").addResourceLocations("file:/D:/HalcyonStorage/");
    }
}
