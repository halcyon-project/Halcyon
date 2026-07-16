package com.ebremer.halcyon.server;

import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.protocol.http.ContextParamWebApplicationFactory;
import org.apache.wicket.protocol.http.WicketFilter;
import static org.apache.wicket.protocol.http.WicketFilter.IGNORE_PATHS_PARAM;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.ebremer.halcyon.gui.HalcyonApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 *
 * @author erich
 */

@Configuration
public class Wicket {
    
    @Bean
    public FilterRegistrationBean<WicketFilter> wicketFilterRegistration(){
        HalcyonApplication hal = new HalcyonApplication();
        hal.setConfigurationType(RuntimeConfigurationType.DEPLOYMENT);
        WicketFilter filter = new WicketFilter(hal);
        filter.setFilterPath("/");
        FilterRegistrationBean<WicketFilter> registration = new FilterRegistrationBean<>();
        registration.setName("HalcyonWicketFilter");
        registration.setFilter(filter);           
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        registration.addInitParameter(ContextParamWebApplicationFactory.APP_CLASS_PARAM, HalcyonApplication.class.getName());
        registration.addInitParameter(WicketFilter.FILTER_MAPPING_PARAM, "/*");
        registration.addInitParameter(IGNORE_PATHS_PARAM, URLControl.getWicketIgnores());
        return registration;
    }
}
