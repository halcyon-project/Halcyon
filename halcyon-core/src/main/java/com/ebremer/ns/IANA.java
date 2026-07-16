package com.ebremer.ns;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 *
 * @author erich
 */
public class IANA {

    public static final String NS = "http://www.w3.org/ns/iana/media-types/";
    
    public static final Resource JSON = ResourceFactory.createResource(NS+"application/json#Resource");
}
