package com.ebremer.halcyon.filereaders;

import java.net.URI;
import java.util.Set;
import org.apache.jena.rdf.model.Model;

/**
 *
 * @author erich
 */
public interface FileReader extends AutoCloseable {
    
    public Model getMeta();
    public Model getMeta(URI uri);
    public String getFormat();
    public Set<String> getSupportedFormats();
}
