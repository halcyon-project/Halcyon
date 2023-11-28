package com.ebremer.halcyon.server.utils;

import java.nio.file.Path;

/**
 *
 * @author erich
 */
public class StorageLocation {
    public Path path;
    public String urlpath;
    
    public StorageLocation(Path path, String urlpath) {
        this.path = path;
        this.urlpath = urlpath;
    }
}
