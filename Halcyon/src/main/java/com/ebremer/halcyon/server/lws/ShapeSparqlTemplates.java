package com.ebremer.halcyon.server.lws;

import com.ebremer.ns.HAL;
import java.util.HashMap;
import java.util.Map;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.vocabulary.SchemaDO;

/**
 * Shape-scoped {@code CONSTRUCT} templates for LDP {@code Prefer}-header
 * handling: when a client asks for a resource "as shaped by" a SHACL
 * shape, {@link Tools#getRDF} pulls the matching template here instead of
 * the generic spanning-tree construct.
 *
 * <p>(Formerly the vandegraph prototype's {@code HShapesSPARQL}; the
 * templates are Halcyon LDP-server behavior, so they live with the
 * server now.)
 */
public final class ShapeSparqlTemplates {

    private static final ShapeSparqlTemplates INSTANCE = new ShapeSparqlTemplates();

    private final Map<String, String> sparql = new HashMap<>();

    private ShapeSparqlTemplates() {
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
        pss.setNsPrefix("hal", HAL.NS);
        sparql.put(HAL.AnnotationClassShape.getURI(), pss.toString());
    }

    public static ShapeSparqlTemplates getInstance() {
        return INSTANCE;
    }

    /** The template registered for {@code shape}, or {@code null}. */
    public ParameterizedSparqlString getPSS(String shape) {
        String template = sparql.get(shape);
        return template == null ? null : new ParameterizedSparqlString(template);
    }
}
