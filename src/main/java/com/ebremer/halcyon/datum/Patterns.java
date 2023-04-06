package com.ebremer.halcyon.datum;

import com.ebremer.ethereal.MakeList;
import static com.ebremer.halcyon.datum.DataCore.Level.OPEN;
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
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class Patterns {
    
    public static List<Node> getCollectionList() {
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
        return list;
    }
    
    public static List<Node> getCollectionList(Model m) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            select ?s ?name
            where {?s a so:Collection; so:name ?name}
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        //Dataset ds = DataCore.getInstance().getSecuredDataset(OPEN);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m);
        //ds.begin(ReadWrite.READ);
        ResultSet rs = qe.execSelect();
        List<Node> list = MakeList.Of(rs, "s");
        //ds.end();
        return list;
    }
    
    public static List<Node> getCollectionList(Dataset ds) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            select ?s ?name
            where {graph ?s {?s a so:Collection; so:name ?name}}
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        System.out.println(pss.toString());
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds);
        ds.begin(ReadWrite.READ);
        ResultSet rs = qe.execSelect().materialise();
        List<Node> list = MakeList.Of(rs, "s");
        ds.end();
        return list;
    }
    
    public static Model getCollectionRDF() {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            construct {?s a so:Collection; so:name ?name}
            where {graph ?s {?s a so:Collection; so:name ?name}}
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        System.out.println("getCollectionRDF()\n"+pss.toString());
        Dataset ds = DataCore.getInstance().getSecuredDataset(OPEN);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds);
        ds.begin(ReadWrite.READ);
        Model m = qe.execConstruct();
        ds.end();
        RDFDataMgr.write(System.out, m, Lang.TURTLE);
        return m;
    }
}
