package com.ebremer.halcyon.services;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ServicesLoader {
    private static final Logger logger = LoggerFactory.getLogger(ServicesLoader.class);
    private ServiceLoader<Service> serviceLoader = null;
    private final List<Service> plugins = new ArrayList<>();
    private static ServicesLoader servicesloader = null;

    private ServicesLoader() {
        logger.info("Starting Service Loader...");
        serviceLoader = ServiceLoader.load(Service.class);
        for (Service plugin : serviceLoader) {
            logger.info("Starting Service Loader...Adding {}", plugin.getName());
            plugins.add(plugin);
        }
    }

    public List<Service> getPlugins() {
        return plugins;
    }
    
    public static void init() {
        if (servicesloader==null) {
            servicesloader = new ServicesLoader();
        }
    }
    
    public static ServicesLoader getInstance() {
        init();
        return servicesloader;
    }
}
