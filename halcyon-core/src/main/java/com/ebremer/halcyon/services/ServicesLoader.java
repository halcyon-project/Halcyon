package com.ebremer.halcyon.services;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;


public class ServicesLoader {
    private final ServiceLoader<Service> serviceLoader;
    private final List<Service> plugins = new ArrayList<>();

    public ServicesLoader() {
        System.out.println("Starting Service Loader...");
        serviceLoader = ServiceLoader.load(Service.class);
        for (Service plugin : serviceLoader) {
            System.out.println("Starting Service Loader...Adding --> "+plugin.getName());
            plugins.add(plugin);
        }
    }

    public List<Service> getPlugins() {
        return plugins;
    }
}
