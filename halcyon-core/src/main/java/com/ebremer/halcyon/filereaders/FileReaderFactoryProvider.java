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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.rdf.model.Resource;
import com.ebremer.halcyon.lib.FileUtils;
import java.io.File;
import java.util.ArrayList;
import org.slf4j.LoggerFactory;

public class FileReaderFactoryProvider {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(FileReaderFactoryProvider.class);
    private static final Map<String, FileReaderFactory> readersMap = new HashMap<>();
    private static FileReaderFactoryProvider frfp = null;

    private FileReaderFactoryProvider(ClassLoader loader) {
        ServiceLoader<FileReaderFactory> loaders = ServiceLoader.load(FileReaderFactory.class, loader);
        for (FileReaderFactory reader : loaders) {
            reader.getSupportedFormats().forEach(f->{
                logger.info("load reader {} {}", f, reader.getClass().toGenericString());
                readersMap.put(f, reader);
            });
        }
        Path rootlib = Path.of("libs");
        if (!rootlib.toFile().exists()) {
            rootlib.toFile().mkdirs();
        }
        try {
            ArrayList<URL> list = new ArrayList<>();
            Files.list(rootlib)                
                .filter(path -> Files.isRegularFile(path))
                .filter(path -> path.toString().toLowerCase().endsWith(".jar"))                
                .map(path -> path.toAbsolutePath())
                //.peek(path -> System.out.println("AARRRRRRRRRRRRGGGGGGGGGGGGGHH ===> "+path))
                .forEach(p->{
                    logger.info("load external readers {} {}", p);
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
            URLClassLoader classLoader = new URLClassLoader(urls, loader);
            ServiceLoader<FileReaderFactory> loaderx = ServiceLoader.load(FileReaderFactory.class, classLoader);
            for (FileReaderFactory impl : loaderx) {
                impl.getSupportedFormats().forEach(f->{
                    System.out.println("GET LOADER ====> "+f);
                    readersMap.put(f, impl);
                });
            }
        } catch (IOException ex) {
            Logger.getLogger(FileReaderFactoryProvider.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void init(ClassLoader loader) {
        logger.info("init");
        if (frfp==null) {
            frfp = new FileReaderFactoryProvider(loader);
        }
    }
    
    public static boolean contains(String format) {
        return readersMap.containsKey(format);
    }
    
    public static boolean hasReaderFor(Resource iri) {
        return contains(FileUtils.getExtension(iri.getURI()));
    }

    public static boolean hasReaderFor(Path iri) {
        File ww = iri.toFile();
        String gg = ww.toString();
        String ext = FileUtils.getExtension(gg);
        return contains(ext);
    }
    
    public static FileReaderFactory getReaderForFormat(Resource iri) {
        System.out.println(iri.getURI());
        //readersMap.forEach((k,v)->{
          //  System.out.println("reader --> "+k+"  "+v.getClass().toGenericString());
        //});      
        String za = iri.getURI().replace("/", "");
        return getReaderForFormat(FileUtils.getExtension(za));
    }
    
    public static FileReaderFactory getReaderForFormat(String format) {
        return readersMap.get(format);
    }
}
