package com.ebremer.halcyon.datum;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.ns.HAL;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.system.Txn;

/**
 *
 * @author erich
 */
public class HalSec {
    
    /**
     * H13: two defects, one of which is a trap.
     * <p>
     * The QueryExecution was never closed — that part is routine. The subtle part:
     * this reads a named model out of the TDB2 dataset with NO transaction of its
     * own. It only ever worked because its single caller,
     * {@code SecuredDatasetGraph.addGraph}, is itself mid-WRITE and so the thread
     * already holds a transaction. Wrapping this in a plain {@code begin(READ)} /
     * {@code end()} — the fix applied everywhere else in this pass — would throw
     * "Currently in an active transaction" and break that caller. Jena's
     * {@code Txn} helper is the right tool: it joins the caller's transaction when
     * there is one and opens (and always closes) its own when there is not.
     */
    public static boolean canCreateCollection(String webid) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString("ask where {?s hal:canCreate hal:Collection}");
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("s", webid);
        // DataCore directly, not the old DatabaseLocator: that went through
        // Wicket's Application.get(), which THROWS off the Wicket request
        // thread — and this runs on Fuseki's threads via SecuredDatasetGraph.
        Dataset ds = DataCore.getInstance().getDataset();
        return Txn.calculateRead(ds, () -> {
            Model m = ds.getNamedModel(HAL.SecurityGraph.getURI());
            try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m)) {
                return qe.execAsk();
            }
        });
    }
}
