package com.ebremer.ns;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
    https://datashapes.org/dash
 */
public class DASH {

    /**
     * The namespace of the vocabulary as a string
     */
    
    public static final String NS ="https://datashapes.org/dash#";
    
    public static final Resource ColorPickerEditor  = ResourceFactory.createResource( NS + "ColorPickerEditor" );
    
    public static final Property editor             = ResourceFactory.createProperty( NS + "editor" );
    
}
