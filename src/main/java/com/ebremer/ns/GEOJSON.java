package com.ebremer.ns;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public class GEOJSON {

/**
 *  A vocabulary and JSON-LD context for GeoJSON.
 *  <p>
 *	See <a href="https://geojson.org/geojson-ld/">GEOJSON-LD Revision 1.1</a>.
 *  <p>
 *  <a href="http://purl.org/geojson/vocab#">Base URI and namepace</a>.
 */
    private static final Model m = ModelFactory.createDefaultModel();
    public static final String NS = "http://purl.org/geojson/vocab#";

    public static final Resource MultiPolygon = m.createResource(NS+"MultiPolygon");
    public static final Resource FeatureCollection = m.createResource(NS+"FeatureCollection");
    public static final Resource GeometryCollection = m.createResource(NS+"GeometryCollection");
    public static final Resource MultiLineString = m.createResource(NS+"MultiLineString");
    public static final Resource Polygon = m.createResource(NS+"Polygon");
    public static final Resource Point = m.createResource(NS+"Point");
    public static final Resource MultiPoint = m.createResource(NS+"MultiPoint");
    public static final Resource Feature = m.createResource(NS+"Feature");
    public static final Resource LineString = m.createResource(NS+"LineString");
    public static final Property bbox = m.createProperty(NS+"bbox");
    public static final Property coordinates = m.createProperty(NS+"coordinates");
    public static final Property type = m.createProperty(NS+"type");
    public static final Property id = m.createProperty(NS+"id");
    public static final Property properties = m.createProperty(NS+"properties");
    public static final Property features = m.createProperty(NS+"features");
    public static final Property geometry = m.createProperty(NS+"geometry");
}
