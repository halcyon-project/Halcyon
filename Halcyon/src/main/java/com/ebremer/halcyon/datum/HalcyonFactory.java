package com.ebremer.halcyon.datum;

import java.util.UUID;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class HalcyonFactory {
    private static final Logger logger = LoggerFactory.getLogger(HalcyonFactory.class);
    
    public static Resource CreateUUIDResource() {
        return ResourceFactory.createResource("urn:uuid:"+UUID.randomUUID().toString());
    }
           
    public static Model CreateCollection(Resource r) {
        logger.debug("CreateCollection()");
        Model m = ModelFactory.createDefaultModel();
        m.add(r, SchemaDO.name,"BLANK COLLECTION NAME");
        m.add(r, RDF.type, SchemaDO.Collection);
        logger.debug("{}", r.getURI());
        return m;
    }   
}