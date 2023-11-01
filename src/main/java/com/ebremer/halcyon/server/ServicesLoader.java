package com.ebremer.halcyon.server;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;


public class ServicesLoader {
    private final ServiceLoader<Service> serviceLoader;
    private final List<Service> plugins = new ArrayList<>();

    public ServicesLoader() {
        serviceLoader = ServiceLoader.load(Service.class);
        for (Service plugin : serviceLoader) {
            plugins.add(plugin);
        }
    }

    public List<Service> getPlugins() {
        return plugins;
    }
}
