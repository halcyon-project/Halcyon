package com.ebremer.halcyon.server;

import com.ebremer.halcyon.gui.HalcyonApplication;
import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.protocol.http.ContextParamWebApplicationFactory;
import org.apache.wicket.protocol.http.WicketFilter;
import static org.apache.wicket.protocol.http.WicketFilter.IGNORE_PATHS_PARAM;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 *
 * @author erich
 */

@Configuration
public class Wicket {
    
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public FilterRegistrationBean<WicketFilter> wicketFilterRegistration(){
        HalcyonApplication hal = new HalcyonApplication();
        hal.setConfigurationType(RuntimeConfigurationType.DEPLOYMENT);
        WicketFilter filter = new WicketFilter(hal);
        filter.setFilterPath("/");
        FilterRegistrationBean<WicketFilter> registration = new FilterRegistrationBean<>();
        registration.setName("HalcyonWicketFilter");
        registration.setFilter(filter);           
        registration.setOrder(5000);
        registration.addInitParameter(ContextParamWebApplicationFactory.APP_CLASS_PARAM, HalcyonApplication.class.getName());
        registration.addInitParameter(WicketFilter.FILTER_MAPPING_PARAM, "/*");
        registration.addInitParameter(IGNORE_PATHS_PARAM, "/skunkworks,/login,/auth,/three.js/,/multi-viewer/,/iiif/,/halcyon/,/images/,/favicon.ico,/rdf,/talon/,/threejs/,/zephyr/,/rdf/");
        return registration;
    }
}
