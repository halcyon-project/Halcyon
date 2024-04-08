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
    
    public static final String NS ="https://id.loc.gov/vocabulary/preservation/cryptographicHashFunctions/";

    public static final Property md5        = ResourceFactory.createProperty( NS + "md5" );
    public static final Property sha1       = ResourceFactory.createProperty( NS + "sha1" );
    public static final Property sha256     = ResourceFactory.createProperty( NS + "sha256" );
    public static final Property sha384     = ResourceFactory.createProperty( NS + "sha384" );
    public static final Property sha512     = ResourceFactory.createProperty( NS + "sha512" );
}
