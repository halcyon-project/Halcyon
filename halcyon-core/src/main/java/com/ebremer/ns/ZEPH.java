package com.ebremer.ns;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

/**
 * Zephyr viewer vocabulary.
 * <p>
 * Base URI and namespace: {@code https://halcyon.is/zephyr/ns/}. Describes
 * stacks of image/feature layers (see the Zephyr WebGL viewer and stack.jsonld).
 */
public class ZEPH {

    private static final Model m = ModelFactory.createDefaultModel();
    public static final String NS = "https://halcyon.is/zephyr/ns/";

    public static final Resource Stack = m.createResource(NS + "Stack");
    public static final Resource ImageLayer = m.createResource(NS + "ImageLayer");
    public static final Resource FeatureLayer = m.createResource(NS + "FeatureLayer");

    public static final Property layers = m.createProperty(NS + "layers");
    public static final Property src = m.createProperty(NS + "src");
    public static final Property zorder = m.createProperty(NS + "zorder");
    public static final Property offsetx = m.createProperty(NS + "offsetx");
    public static final Property offsety = m.createProperty(NS + "offsety");
    public static final Property scalex = m.createProperty(NS + "scalex");
    public static final Property scaley = m.createProperty(NS + "scaley");
    public static final Property pixelsizeX = m.createProperty(NS + "pixelsizeX");
    public static final Property pixelsizeY = m.createProperty(NS + "pixelsizeY");
    public static final Property x = m.createProperty(NS + "x");
    public static final Property y = m.createProperty(NS + "y");
}
