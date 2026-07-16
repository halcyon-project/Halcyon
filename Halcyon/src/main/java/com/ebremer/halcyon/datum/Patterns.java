package com.ebremer.halcyon.datum;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.vandegraph.Solution;
import com.ebremer.ns.HAL;
import com.ebremer.ns.LWS;
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
    // H13: kept commented out, but CORRECTED in place — as written this was the
    // leak template the live methods were copied from (unclosed QueryExecution,
    // unguarded end(), and begin() called AFTER create()). Uncommenting the old
    // version would have reintroduced the defect.
    public static List<Node> getCollectionList(Dataset ds) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            select ?s ?name
            where {graph ?s {?s a so:Collection; so:name ?name}}
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        ds.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds)) {
            return Solution.nodes(qe.execSelect().materialise(), "s");
        } finally {
            ds.end();
        }
    }*/
    
    public static Model getCollectionRDF2(Dataset ds) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            construct {?s a lws:Container; dct:title ?name}
            where {
                graph ?g {?s a lws:Container; dct:title ?name}
            }
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("lws", LWS.NS);
        pss.setNsPrefix("dct", DCTerms.NS);
        pss.setIri("g", HAL.CollectionsAndResources.getURI());
        // H13: the QueryExecution was never closed, and begin() sat INSIDE the try —
        // so a throw from begin() itself ran end() against a non-transaction, masking
        // the real error. Same shape as getALLCollectionRDF below.
        ds.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds)) {
            return qe.execConstruct();
        } finally {
            ds.end();
        }
    }
    
    /**
     * The collections the caller may read, for label lookup.
     * <p>
     * H6: was an unfiltered read of the RAW dataset, so it handed back every
     * container in the store regardless of ACL. Two things had to change together:
     * the dataset is now the WAC-secured one, AND {@code ?g} is BOUND to
     * CollectionsAndResources instead of left as a variable. The binding is not
     * cosmetic — a variable {@code GRAPH ?g} is answered through
     * {@code SecuredDatasetGraph.findNG}, which hands back the base iterator RAW
     * once graph-level access passes, so the per-triple (per-container) filter
     * would never run and this would still return everything. With a constant
     * graph, ARQ routes through {@code getGraph} -> a jena-permissions secured
     * graph -> each triple authorized by its subject.
     */
    public static Model getALLCollectionRDF() {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            construct {?s a lws:Container; dct:title ?name}
            where {graph ?g {?s a lws:Container; dct:title ?name}}
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("lws", LWS.NS);
        pss.setNsPrefix("dct", DCTerms.NS);
        pss.setIri("g", HAL.CollectionsAndResources.getURI());
        Dataset ds = DataCore.getInstance().getSecuredDataset(DataCore.Level.OPEN);
        ds.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds)) {
            return qe.execConstruct();
        } finally {
            ds.end();
        }
    }
        
    public static List<Node> getCollectionList45X(Model m) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            select ?s
            where {?s a lws:Container; dct:title ?name}
            order by ?name
        """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("lws", LWS.NS);
        pss.setNsPrefix("dct", DCTerms.NS);
        // H13: in-memory model, so no transaction to strand — but still close it.
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m)) {
            return Solution.nodes(qe.execSelect(), "s");   // consumes the ResultSet
        }
    }
}
