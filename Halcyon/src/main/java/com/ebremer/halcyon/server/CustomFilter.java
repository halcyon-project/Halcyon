package com.ebremer.halcyon.server;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

public class CustomFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String query = httpRequest.getParameter("query");
        if (query!=null) {
            HttpServletRequest r = (HttpServletRequest) request;
            System.out.println("SPECIAL DETECTED!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n"+r.getRequestURL()+"  "+r.getQueryString());
            //CustomRequestWrapper wrappedRequest = new CustomRequestWrapper(httpRequest);
            //wrappedRequest.addParameter("furi", "Erich Bremer was here");
            String uri = r.getRequestURI();
            String contextPath = r.getContextPath();
            String path = uri.substring(contextPath.length());
            String jet = contextPath+"/raptor"+path;
            RequestDispatcher dispatcher = request.getRequestDispatcher(jet);
            dispatcher.forward(request, response);
        } else {
            chain.doFilter(request, response);
        }
    }
}