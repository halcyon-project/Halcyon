package com.ebremer.halcyon.utils;

import com.ebremer.halcyon.datum.DataCore;
import com.ebremer.ns.HAL;
import jakarta.json.JsonObject;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.vocabulary.RDF;

/**
 *
 * @author erich
 */
public class Hutil {
    
    public Hutil() {
        
    }
    
    public JsonObject getFeatures(String uri) {
        DataCore datacore = DataCore.getInstance();
        Dataset ds = datacore.getDataset();
        ds.begin(ReadWrite.READ);
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            select distinct ?g ?feature ?type ?label ?value
            where {graph ?g {?g hal:hasFeature ?feature .
                ?feature a ?type; rdfs:label ?label; rdf:value ?value
            }}
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("rdf", RDF.getURI());
        pss.setIri("g", uri);
        ResultSet rs = QueryExecutionFactory.create(pss.toString(), ds).execSelect();
        ResultSetFormatter.out(System.out, rs);
        
        
//        while (results.hasNext()) {
//            QuerySolution soln = results.nextSolution();
//            System.out.println(soln.get("s").toString());
//        }
        ds.end();
        return null;
    }
    
}
