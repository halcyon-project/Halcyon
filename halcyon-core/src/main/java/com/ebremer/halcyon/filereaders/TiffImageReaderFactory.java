package com.ebremer.halcyon.filereaders;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author erich
 */
public class TiffImageReaderFactory implements FileReaderFactory {

    @Override
    public FileReader create(URI uri, URI base) {
        try {
            return new TiffImageReader(uri, base);
        } catch (IOException ex) {
            Logger.getLogger(TiffImageReaderFactory.class.getName()).log(Level.SEVERE, null, ex);
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
        set.add("tif");
        set.add("tiff");
        return set;
    }
}
