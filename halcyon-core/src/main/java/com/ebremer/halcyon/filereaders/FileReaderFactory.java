package com.ebremer.halcyon.filereaders;

import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.util.Set;

/**
 *
 * @author erich
 */
public interface FileReaderFactory {
    public FileReader create(URI uri, URI base);
    public FileReader create(SeekableByteChannel src, URI base);
    public Set<String> getSupportedFormats();    
}
