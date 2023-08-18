package com.ebremer.halcyon.imagebox.TE;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;


public class PluginLoader {
    private final ServiceLoader<AbstractImageReader> serviceLoader;
    private final List<AbstractImageReader> plugins = new ArrayList<>();

    public PluginLoader() {
        serviceLoader = ServiceLoader.load(AbstractImageReader.class);
        for (AbstractImageReader plugin : serviceLoader) {
            plugins.add(plugin);
        }
    }

    public List<AbstractImageReader> getPlugins() {
        return plugins;
    }
}
