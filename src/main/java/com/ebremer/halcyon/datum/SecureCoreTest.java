package com.ebremer.halcyon.datum;

import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.WAC;

/**
 *
 * @author erich
 */
public class SecureCoreTest {
    
    public static void main(String[] args) {
        loci.common.DebugTools.setRootLevel("WARN");
        DataCore datacore = DataCore.getInstance();
       // Dataset ds = datacore.getSecuredDataset("urn:uuid:48c07c4e-c092-4180-b67b-b57b6ec60d3d");
        //Dataset ds = datacore.getDataset();
        /*
        DataCore datacore = DataCore.getInstance();
        Dataset ds = datacore.getSecuredDataset("urn:uuid:48c07c4e-c092-4180-b67b-b57b6ec60d3d");
        ds.begin(ReadWrite.READ);
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select *
            where {graph ?g {?s ?p ?o}}
            """);
        pss.setNsPrefix("acl", WAC.NS);
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        pss.setIri("g", HAL.SecurityGraph.getURI());
        ResultSet results = QueryExecutionFactory.create(pss.toString(), ds).execSelect();
        ResultSetFormatter.out(System.out,results);
        ds.end();
        ds.close();
        datacore.shutdown();*/
        //List<Node> list = Patterns.getCollectionList(ds);
  //      System.out.println("DUMP COLLECTIONS...");
        //list.forEach(f->{
//            System.out.println(f);
//        });
    }
}
