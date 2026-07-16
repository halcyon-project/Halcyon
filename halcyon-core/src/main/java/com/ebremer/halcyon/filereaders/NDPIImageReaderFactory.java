package com.ebremer.halcyon.filereaders;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class NDPIImageReaderFactory implements FileReaderFactory {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(NDPIImageReaderFactory.class);

    @Override
    public FileReader create(URI uri, URI base) {
        logger.info("create {} {}", uri, base);
        try {
            return new NDPIImageReader(uri, base);
        } catch (IOException ex) {
            Logger.getLogger(NDPIImageReaderFactory.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public FileReader create(SeekableByteChannel src, URI base) {
        logger.info("create(SeekableByteChannel src, URI base) {} {}", src, base);
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Set<String> getSupportedFormats() {
        Set<String> set = new HashSet<>();
        set.add("ndpi");
        return set;
    }
}
