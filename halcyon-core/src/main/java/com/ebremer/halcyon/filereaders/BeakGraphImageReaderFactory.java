package com.ebremer.halcyon.filereaders;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class BeakGraphImageReaderFactory implements FileReaderFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(BeakGraphImageReaderFactory.class);
    
    @Override
    public FileReader create(URI uri, URI base) {
        logger.trace("create {} {}", uri, base);
        try {     
            return new BeakGraphImageReader(uri, base);
        } catch (IOException ex) {
            System.getLogger(BeakGraphImageReaderFactory.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        return null;
    }

    @Override
    public FileReader create(SeekableByteChannel src, URI base) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Set<String> getSupportedFormats() {
        Set<String> set = new HashSet<>();
        set.add("h5");
        return set;
    }
}
