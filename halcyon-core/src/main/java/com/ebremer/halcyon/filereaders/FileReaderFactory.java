package com.ebremer.halcyon.filereaders;

import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.util.Set;

/**
 *
 * @author erich
 */
public interface FileReaderFactory {
    public FileReader create(URI uri);
    public FileReader create(SeekableByteChannel src);
    public Set<String> getSupportedFormats();    
}
