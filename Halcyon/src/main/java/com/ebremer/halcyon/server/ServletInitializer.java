package com.ebremer.halcyon.server;

import com.ebremer.halcyon.lib.OperatingSystemInfo;
import com.ebremer.halcyon.server.lws.LWSServer;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import jakarta.servlet.Servlet;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Erich Bremer
 */
    public class ServletInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final Logger logger = LoggerFactory.getLogger(ServletInitializer.class);

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            HalcyonSettings.getSettings().GetResourceHandlers().forEach(rh -> {
                ServletRegistrationBean<Servlet> srb = new ServletRegistrationBean();
                srb.setLoadOnStartup(3);
                String name = "LWS " + UUID.randomUUID().toString();
                srb.setBeanName(name);
                srb.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
                if (OperatingSystemInfo.ifWindows()) {
                    srb.addInitParameter("resourceBase", rh.resourceBase().getPath().substring(1));
                    logger.debug("Add Path --> {}  {}", rh.urlPath(), rh.resourceBase().getPath().substring(1));
                } else {                
                    srb.addInitParameter("resourceBase", rh.resourceBase().getPath());
                    logger.debug("Add Path --> {}  {}", rh.urlPath(), rh.resourceBase().getPath());
                }
                // C1: no anonymous directory listing of the stored files (these
                // trees hold PHI). Serving a known resource still works.
                srb.addInitParameter("dirAllowed", "false");
                srb.setServlet(new LWSServer());
                srb.setUrlMappings(Arrays.asList(rh.urlPath() + "*"));
                applicationContext.getBeanFactory().registerSingleton(name, srb);
            });
            ServletRegistrationBean<Servlet> srb = new ServletRegistrationBean();
            srb.setLoadOnStartup(3);
            String name = "LWS " + UUID.randomUUID().toString();
            srb.setBeanName(name);
            srb.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
            srb.addInitParameter("resourceBase", "D:/HalcyonStorage/users/");
            // C1: no anonymous directory listing of the users' storage tree.
            srb.addInitParameter("dirAllowed", "false");
            srb.setServlet(new LWSServer());
            srb.setUrlMappings(Arrays.asList("/users/*"));
            applicationContext.getBeanFactory().registerSingleton(name, srb);
        }
    }
