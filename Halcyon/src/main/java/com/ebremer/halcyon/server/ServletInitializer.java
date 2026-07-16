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

/**
 *
 * @author Erich Bremer
 */
    public class ServletInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

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
                    System.out.println("Add Path --> " + rh.urlPath() + "  " + rh.resourceBase().getPath().substring(1));
                } else {                
                    srb.addInitParameter("resourceBase", rh.resourceBase().getPath());
                    System.out.println("Add Path --> " + rh.urlPath() + "  " + rh.resourceBase().getPath());
                }
                srb.addInitParameter("dirAllowed", "true");
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
            srb.addInitParameter("dirAllowed", "true");
            srb.setServlet(new LWSServer());
            srb.setUrlMappings(Arrays.asList("/users/*"));
            applicationContext.getBeanFactory().registerSingleton(name, srb);
        }
    }
