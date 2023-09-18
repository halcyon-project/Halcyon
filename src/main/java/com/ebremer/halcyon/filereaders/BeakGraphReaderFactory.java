package com.ebremer.halcyon.filereaders;

import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author erich
 */
public class BeakGraphReaderFactory implements FileReaderFactory {

    @Override
    public BeakGraphFileReader create(URI uri) {
        return new BeakGraphFileReader(uri);
    }

    @Override
    public FileReader create(SeekableByteChannel src) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Set<String> getSupportedFormats() {
        Set<String> set = new HashSet<>();
        set.add("zip");
        return set;
    }
}
