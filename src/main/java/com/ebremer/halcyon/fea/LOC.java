package com.ebremer.ns;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;

/**
    id.loc.gov vocabulary items
 */
public class LOC {

    /**
     * The namespace of the vocabulary as a string
     */
    
    public static final String NS ="http://id.loc.gov/vocabulary/preservation/cryptographicHashFunctions/";

    public static final Property md5             = ResourceFactory.createProperty( NS + "md5" );
    
}
