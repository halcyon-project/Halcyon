package com.ebremer.halcyon.raptor;

import com.ebremer.ns.GEO;
import com.ebremer.ns.HAL;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.update.UpdateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class FeatureGeneration {
    private static final Logger logger = LoggerFactory.getLogger(FeatureGeneration.class);
    
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
        try {
            UpdateAction.parseExecute(pssx.toString(), m);
        } catch (Exception ex) {
            logger.debug("AddAreas -> {}", ex.getMessage());
        } catch ( Throwable t ) {
            logger.error("Unhandled exception", t);
        }
        logger.debug("Done adding areas...");
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
        // H13: in-memory model, but close the execution.
        try (QueryExecution qe = QueryExecutionFactory.create(pssx.toString(), m)) {
            ResultSet rs = qe.execSelect();
            logger.debug("results --> {}", rs.hasNext());
            ResultSetFormatter.out(System.out, rs);
        }
        int c = 0;
    }
}
