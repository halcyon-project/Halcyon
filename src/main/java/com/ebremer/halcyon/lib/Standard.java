package com.ebremer.halcyon.lib;

import com.ebremer.beakgraph.ng.BG;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class Standard {
    
    public static PrefixMapping getStandardPrefixes() {
        PrefixMapping map = PrefixMapping.Factory.create()
            .setNsPrefix("", HAL.NS)
            .setNsPrefix("rdf", RDF.uri)
            .setNsPrefix("rdfs", RDFS.uri)
            .setNsPrefix("so", SchemaDO.NS)
            .setNsPrefix("oa", OA.NS)
            .setNsPrefix("exif", EXIF.NS)
            .setNsPrefix("hal", HAL.NS)
            .setNsPrefix("bg", BG.NS)
            .setNsPrefix("dcmi", DCTerms.NS);
        return map;
    }
}
