package com.ebremer.halcyon.raptor;

import com.ebremer.ns.GEO;
import com.ebremer.ns.HAL;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.update.UpdateAction;

/**
 *
 * @author erich
 */
public class FeatureGeneration {
    
    public static void AddPerimeters(Model m) {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            insert {
                ?feature geo:hasPerimeterLength ?perimeter
            }
            where {
                ?feature geo:hasGeometry/geo:asWKT ?wkt
                bind (hal:perimeter(?wkt) as ?perimeter)
            }
            """
        );
        pssx.setNsPrefix("geo", GEO.NS);
        pssx.setNsPrefix("hal", HAL.NS);
        UpdateAction.parseExecute(pssx.toString(), m);
       // Display(m);
    }

    public static void AddAreas(Model m) {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            insert {
                ?feature geo:hasArea ?area
            }
            where {
                ?feature geo:hasGeometry/geo:asWKT ?wkt
                bind (hal:area(?wkt) as ?area)
            }
            """
        );
        pssx.setNsPrefix("geo", GEO.NS);
        pssx.setNsPrefix("hal", HAL.NS);
        UpdateAction.parseExecute(pssx.toString(), m);
//        Display(m);
    }
    
    public static void Display(Model m) {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            select ?feature ?perimeter ?area
            where {
                ?feature geo:hasArea ?area; geo:hasPerimeterLength ?perimeter; geo:hasGeometry/geo:asWKT ?wkt
            }
            """
        );
        pssx.setNsPrefix("geo", GEO.NS);
        ResultSet rs = QueryExecutionFactory.create(pssx.toString(), m).execSelect();
        System.out.println("results --> "+rs.hasNext());
        ResultSetFormatter.out(System.out, rs);
        int c = 0;
    }
}
