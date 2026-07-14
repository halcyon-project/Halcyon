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
public class JPEG2000ImageReaderFactory implements FileReaderFactory {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(JPEG2000ImageReaderFactory.class);

    @Override
    public FileReader create(URI uri, URI base) {
        logger.info("create {} {}", uri, base);
        try {
            return new JPEG2000ImageReader(uri, base);
        } catch (IOException ex) {
            Logger.getLogger(JPEG2000ImageReaderFactory.class.getName()).log(Level.SEVERE, null, ex);
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
        set.add("jp2");
        set.add("j2k");
        set.add("j2c");
        set.add("jpc");
        set.add("jph");
        set.add("jhc");
        return set;
    }
}
