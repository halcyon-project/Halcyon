package com.ebremer.halcyon.datum;

import java.util.UUID;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class HalcyonFactory {
    
    public static Resource CreateUUIDResource() {
        return ResourceFactory.createResource("urn:uuid:"+UUID.randomUUID().toString());
    }
           
    public static Model CreateCollection(Resource r) {
        System.out.println("CreateCollection()");
        Model m = ModelFactory.createDefaultModel();
        m.add(r, SchemaDO.name,"BLANK COLLECTION NAME");
        m.add(r, RDF.type, SchemaDO.Collection);
        RDFDataMgr.write(System.out, m, RDFFormat.TURTLE_PRETTY);
        System.out.println(r.getURI());
        return m;
    }   
}