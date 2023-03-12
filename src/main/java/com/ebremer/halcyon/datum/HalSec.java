package com.ebremer.halcyon.datum;

import com.ebremer.halcyon.wicket.DatabaseLocator;
import com.ebremer.ns.HAL;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;

/**
 *
 * @author erich
 */
public class HalSec {
    
    public static boolean canCreateCollection(String webid) {
        System.out.println("canCreateCollection "+webid);
        ParameterizedSparqlString pss = new ParameterizedSparqlString("ask where {?s hal:canCreate hal:Collection}");
        pss.setNsPrefix("hal", HAL.NS);
        Model m = DatabaseLocator.getDatabase().getDataset().getNamedModel(HAL.SecurityGraph.getURI());
        pss.setIri("s", webid);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),m);
        return qe.execAsk();
    }    
}
