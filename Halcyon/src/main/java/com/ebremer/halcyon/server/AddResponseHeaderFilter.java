package com.ebremer.halcyon.server;

import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AddResponseHeaderFilter implements Filter {

    private final Integer serverPort = 8888;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (HalcyonSettings.getSettings().isHTTPS3enabled()) {
            System.out.println("ADD HTTP/3 Header!!!");
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            httpServletResponse.setHeader( "Alt-Svc", "h3=\":" + serverPort + "\"; ma=86400; persist=1");
        }
        HalcyonSession ha;
        chain.doFilter(request, response);
    }
}
