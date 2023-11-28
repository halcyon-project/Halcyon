package com.ebremer.halcyon.filereaders;

import java.net.URI;

/**
 *
 * @author erich
 */
public abstract class AbstractFileReader implements FileReader {
    protected final URI uri;
    
    public AbstractFileReader(URI uri) {
        this.uri = uri;
    }
}
