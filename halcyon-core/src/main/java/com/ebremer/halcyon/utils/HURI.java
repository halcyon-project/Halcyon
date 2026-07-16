package com.ebremer.halcyon.utils;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author erich
 */
public class HURI {

    public static URI getParent(URI uri) {
        String[] parts = uri.getPath().split("/");
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<parts.length-1; i++) {
            sb.append(parts[i]).append("/");
        }
        try {
            return new URI(uri.getScheme(),uri.getUserInfo(),uri.getHost(), uri.getPort(), sb.toString(), null, null);
        } catch (URISyntaxException ex) {
            Logger.getLogger(HURI.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static URI of(String uri) {
        URI nuri = URI.create(uri);
        return nuri;
    }
    
    public static URI of(Path path) {
        URI uri = path.toUri();
        try {
            return new URI(uri.getScheme(), "", uri.getPath(), null, null);
        } catch (URISyntaxException ex) {
            Logger.getLogger(HURI.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public static URI of(URI furi) {
        try {
            return new URI(furi.getScheme(), "", furi.getPath(), null, null);
        } catch (URISyntaxException ex) {
            Logger.getLogger(HURI.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public static URI of(File file) {
        URI furi = file.toURI();
        try {
            return new URI(furi.getScheme(), "", furi.getPath(), null, null);
        } catch (URISyntaxException ex) {
            Logger.getLogger(HURI.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public static void main(String[] args) throws URISyntaxException {
        URI uri = new URI("https://localhost:8888/a/b/c/d/e/f/image.svs");
        System.out.println(getParent(uri));
    }
}
