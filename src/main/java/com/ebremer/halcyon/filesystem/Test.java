package com.ebremer.halcyon.filesystem;

import java.io.FileNotFoundException;
import java.util.List;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 *
 * @author erich
 */
public class Test {
    
    public static void main(String[] args) throws FileNotFoundException {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select * where {
                ?s ?p ?o
                values (?p ?o) {?z}
            """);

            List x =  List.of(
                    List.of(ResourceFactory.createResource("http://me.com/1"), ResourceFactory.createTypedLiteral("A")),
                    List.of(ResourceFactory.createResource("http://me.com/2"), ResourceFactory.createTypedLiteral("B")),
                    List.of(ResourceFactory.createResource("http://me.com/3"), ResourceFactory.createTypedLiteral("C")),
                    List.of(ResourceFactory.createResource("http://me.com/4"), ResourceFactory.createTypedLiteral("D")),
                    List.of(ResourceFactory.createResource("http://me.com/5"), ResourceFactory.createTypedLiteral("E")));
            pss.setRowValues("z", x);
        System.out.println(pss.toString());
    }

    
    
}


/*



*/