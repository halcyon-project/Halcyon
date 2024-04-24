package com.ebremer.halcyon.datum;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.ethereal.MakeList;
import com.ebremer.ns.HAL;
import com.ebremer.ns.LDP;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.DCTerms;

/**
 *
 * @author erich
 */
public class Patterns {
    
    /*
    public static List<Node> getCollectionList(Dataset ds) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            select ?s ?name
            where {graph ?s {?s a so:Collection; so:name ?name}}
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds);
        ds.begin(ReadWrite.READ);
        ResultSet rs = qe.execSelect().materialise();
        List<Node> list = MakeList.Of(rs, "s");
        ds.end();
        return list;
    }*/
    
    public static Model getCollectionRDF2(Dataset ds) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            construct {?s a ldp:Container; dct:title ?name}
            where {
                graph ?g {?s a ldp:Container; dct:title ?name}
            }
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("ldp", LDP.NS);
        pss.setNsPrefix("dct", DCTerms.NS);
        pss.setIri("g", HAL.CollectionsAndResources.getURI());
        System.out.println(pss.toString());
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds);
        Model m;
        try {
            ds.begin(ReadWrite.READ);
            m = qe.execConstruct();
        } finally {
            ds.end();
        }
        return m;
    }
    
    public static Model getALLCollectionRDF() {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            construct {?s a ldp:Container; dct:title ?name}
            where {graph ?g {?s a ldp:Container; dct:title ?name}}
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("ldp", LDP.NS);
        pss.setNsPrefix("dct", DCTerms.NS);
        Dataset ds = DataCore.getInstance().getDataset();
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds);
        ds.begin(ReadWrite.READ);
        Model m = qe.execConstruct();
        ds.end();
        return m;
    }
        
    public static List<Node> getCollectionList45X(Model m) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            select ?s
            where {?s a ldp:Container; dct:title ?name}
            order by ?name
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("ldp", LDP.NS);
        pss.setNsPrefix("dct", DCTerms.NS);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m);
        ResultSet rs = qe.execSelect();
        List<Node> list = MakeList.Of(rs, "s");
        return list;
    }
}
