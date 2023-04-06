package com.ebremer.halcyon.imagebox;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

public class JwtInterceptor implements Filter {
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        //System.out.println("--------------------> postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) ");
        //httpResponse.setHeader("Custom-Header", "Custom-Value");
        
        /*
            KeycloakSecurityContext securityContext = (KeycloakSecurityContext) request.getAttribute(KeycloakSecurityContext.class.getName());
            if (securityContext != null) {
                System.out.println("ADDING TOKEN!!!");
                String jwtToken = securityContext.getTokenString();
                httpResponse.setHeader("token", jwtToken);
            } else {
                System.out.println("NOTHING TO ADD!!!");
            }
       */
        chain.doFilter(request, response);
    }
    
    @Override
    public void destroy() {
    }
    
}
