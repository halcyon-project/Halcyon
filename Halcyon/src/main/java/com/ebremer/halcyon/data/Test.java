package com.ebremer.halcyon.data;

import static com.ebremer.halcyon.data.DataCore.Level.CLOSED;
import com.ebremer.ns.HAL;
import org.apache.jena.graph.Graph;
import org.apache.jena.permissions.graph.SecuredGraph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.tdb2.TDB2Factory;


/**
 *
 * @author erich
 */
public class Test {

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        Dataset x = TDB2Factory.connectDataset("/projects/Halcyon/Halcyon/tdb2");
        Dataset ds = DatasetFactory.wrap(new SecuredDatasetGraph(x.asDatasetGraph(), new WACSecurityEvaluator(CLOSED)));
        QueryExecution qe = QueryExecutionFactory.create(
            """
            select *
            where { graph <https://halcyon.is/ns/CollectionsAndResources> {
                 ?s ?p ?o
                 }
            }
            """, ds);
        ds.begin(ReadWrite.READ);
        ResultSetFormatter.out(System.out,qe.execSelect());
        ds.end();
        
    }
}
