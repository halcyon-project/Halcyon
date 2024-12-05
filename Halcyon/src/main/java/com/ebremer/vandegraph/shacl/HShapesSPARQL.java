package com.ebremer.vandegraph.shacl;

import com.ebremer.ns.HAL;
import java.util.HashMap;
import java.util.Map;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class HShapesSPARQL {
    private static HShapesSPARQL hss = null;
    
    private final Map<String,String> sparql;
    
    private HShapesSPARQL() {
        sparql = new HashMap<>();
        //HShapes shapes = new HShapes();
        //Model m = shapes.getShapes();
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            construct {
                ?s ?sp ?so; hal:hasAnnotationClass ?AnnotationClass .
                    ?AnnotationClass ?AnnotationClassP1 ?AnnotationClassOR; ?AnnotationClassP2 ?AnnotationClassOL .
                    ?AnnotationClassOR sdo:name ?name
            } where {
                ?s ?sp ?so; hal:hasAnnotationClass ?AnnotationClass .
                ?AnnotationClass ?AnnotationClassP1 ?AnnotationClassOR; ?AnnotationClassP2 ?AnnotationClassOL .
                ?AnnotationClassOR sdo:name ?name
            }
            """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("sdo", SchemaDO.NS);
        sparql.put(HAL.AnnotationClassListShape.getURI(), pss.toString());
        pss = new ParameterizedSparqlString(
            """
            construct {
                ?s hal:annotation ?anno
            } where {
                ?s hal:annotation ?anno          
            }
            """);
        sparql.put(HAL.AnnotationClassShape.getURI(), pss.toString());
    }
    
    public static HShapesSPARQL getInstance() {
        if (hss == null) {
            hss = new HShapesSPARQL();
        }
        return hss;
    }
    
    public ParameterizedSparqlString getPSS(String shape) {
        if (sparql.containsKey(shape)) {
            return new ParameterizedSparqlString(sparql.get(shape));
        }
        return null;
    }
    
    public static void main(String args[]) {
        ParameterizedSparqlString wow = HShapesSPARQL.getInstance().getPSS("https://halcyon.is/ns/AnnotationClassListShape");
        System.out.println(wow.toString());
        wow.setIri("s", "https://www.ebremer.com");
        System.out.println(wow.toString());
    }
}
