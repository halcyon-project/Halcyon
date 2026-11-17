package com.ebremer.halcyon.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
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
        // Wicket's own ignore list (IGNORE_PATHS_PARAM) matches by string prefix, which is right
        // for a mount's tree ("/lws/") and wrong for the mount itself: "/lws" as a prefix also
        // takes the page /lwscontainers away from Wicket. So the bare mount paths are matched
        // here, exactly, and handed down the chain to their servlets.
        Set<String> exact = URLControl.getWicketIgnoredExactPaths();
        WicketFilter filter = new WicketFilter(hal) {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                if (request instanceof HttpServletRequest http && exact.contains(pathOf(http))) {
                    chain.doFilter(request, response);
                    return;
                }
                super.doFilter(request, response, chain);
            }
        };
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

    /** The request's path within the application: no context path, no ;path parameters. */
    static String pathOf(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        int semicolon = path.indexOf(';');
        return semicolon < 0 ? path : path.substring(0, semicolon);
    }
}
