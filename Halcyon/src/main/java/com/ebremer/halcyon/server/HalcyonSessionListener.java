package com.ebremer.halcyon.server;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HalcyonSessionListener implements HttpSessionListener {

    private  static final Logger logger = LoggerFactory.getLogger(HalcyonSessionListener.class);

    @Override
    public void sessionCreated(final HttpSessionEvent event) {
        System.out.println("================================================== Session created");
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent var1) {
        System.out.println("================================================== Session destroyed");
    }
}
