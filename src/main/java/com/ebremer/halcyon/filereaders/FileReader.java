package com.ebremer.halcyon.filereaders;

import java.util.Set;
import org.apache.jena.rdf.model.Model;

/**
 *
 * @author erich
 */
public interface FileReader extends AutoCloseable {
    public Model getMeta();
    public String getFormat();
    public Set<String> getSupportedFormats();
}
