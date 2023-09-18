package com.ebremer.halcyon.filereaders;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.rdf.model.Resource;
import com.ebremer.halcyon.lib.FileUtils;

public class FileReaderFactoryProvider {

    private static final Map<String, FileReaderFactory> readersMap = new HashMap<>();

    static {
        ServiceLoader<FileReaderFactory> loaders = ServiceLoader.load(FileReaderFactory.class);
        for (FileReaderFactory reader : loaders) {
            reader.getSupportedFormats().forEach(f->{
                readersMap.put(f, reader);
            });
        }
        Path rootlib = Path.of("libs");
        if (!rootlib.toFile().exists()) {
            rootlib.toFile().mkdirs();
        }
        try {
            LinkedList<URL> list = new LinkedList<>();
            Files.list(rootlib)
                .filter(path -> Files.isRegularFile(path))
                .filter(path -> path.toString().toLowerCase().endsWith(".jar"))
                .map(path -> path.toAbsolutePath())
                .forEach(p->{
                    URI uri = p.toUri();
                    try {
                        list.add(uri.toURL());
                    } catch (MalformedURLException ex) {
                        Logger.getLogger(FileReaderFactoryProvider.class.getName()).log(Level.SEVERE, null, ex);
                    }
            });
            URL[] urls = new URL[list.size()];
            for (int i=0; i<list.size(); i++) {
                urls[i] = list.get(i);
            }
            URLClassLoader classLoader = new URLClassLoader(urls);
            ServiceLoader<FileReaderFactory> loader = ServiceLoader.load(FileReaderFactory.class, classLoader);
            for (FileReaderFactory impl : loader) {
                impl.getSupportedFormats().forEach(f->{
                    readersMap.put(f, impl);
                });
            }
        } catch (IOException ex) {
            Logger.getLogger(FileReaderFactoryProvider.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static boolean contains(String format) {
        return readersMap.containsKey(format);
    }
    
    public static boolean hasReaderFor(Resource iri) {
        return contains(FileUtils.getExtension(iri.getURI()));
    }
    
    public static FileReaderFactory getReaderForFormat(Resource iri) {
        return getReaderForFormat(FileUtils.getExtension(iri.getURI()));
    }
    
    public static FileReaderFactory getReaderForFormat(String format) {
        return readersMap.get(format);
    }
}
