package com.ebremer.halcyon.datum;

import com.ebremer.ethereal.MakeList;
//import com.ebremer.halcyon.utils.StopWatch;
import com.ebremer.ns.HAL;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class Patterns {
    
    public static List<Node> getCollectionList() {
        //StopWatch w = new StopWatch(true);
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            select ?s ?name
            where {graph ?s {?s a so:Collection; so:name ?name}}
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        Dataset ds = DataCore.getInstance().getSecuredDataset();
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds);
        ds.begin(ReadWrite.READ);
        ResultSet rs = qe.execSelect();
        List<Node> list = MakeList.Of(rs, "s");
        ds.end();
        //w.getTime("time to get getCollectionList()");
        return list;
    }
    
    public static List<Node> getCollectionList(Model m) {
        //StopWatch w = new StopWatch(true);
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            select ?s ?name
            where {?s a so:Collection; so:name ?name}
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        Dataset ds = DataCore.getInstance().getSecuredDataset();
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m);
        ds.begin(ReadWrite.READ);
        ResultSet rs = qe.execSelect();
        List<Node> list = MakeList.Of(rs, "s");
        ds.end();
        //w.getTime("getCollectionList(Model m)");
        return list;
    }
    
    public static List<Node> getCollectionList(Dataset ds) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            select ?s ?name
            where {graph ?s {?s a so:Collection; so:name ?name}}
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds);
        ds.begin(ReadWrite.READ);
        ResultSet rs = qe.execSelect();
        List<Node> list = MakeList.Of(rs, "s");
        ds.end();
        return list;
    }
    
    public static Model getCollectionRDF() {
        //StopWatch w = new StopWatch(true);
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            construct {?s a so:Collection; so:name ?name}
            where {graph ?s {?s a so:Collection; so:name ?name}}
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        Dataset ds = DataCore.getInstance().getDataset();
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds);
        ds.begin(ReadWrite.READ);
        Model m = qe.execConstruct();
        ds.end();
        //w.getTime("getCollectionRDF()");
        return m;
    }
}
