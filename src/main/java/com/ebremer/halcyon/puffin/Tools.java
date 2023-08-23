package com.ebremer.halcyon.puffin;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

/**
 *
 * @author erich
 */
public class Tools {
    
    public static Triple newTriple(Model m, Triple triple, Object newobj) {
        Node s = triple.getSubject();
        Node p = triple.getPredicate();
        Node o;
        switch (newobj) {
            case Float value -> o = NodeFactory.createLiteralByValue(value, org.apache.jena.datatypes.xsd.XSDDatatype.XSDfloat);
            case Integer value -> o = NodeFactory.createLiteralByValue(value, org.apache.jena.datatypes.xsd.XSDDatatype.XSDinteger);
            case String value -> o = NodeFactory.createLiteralByValue(value, org.apache.jena.datatypes.xsd.XSDDatatype.XSDstring);
            case Resource value -> o = NodeFactory.createURI(value.getURI());
            default -> throw new Error("I CAN HANDLE THIS !!  --> "+newobj);
        }
        return Triple.create(s, p, o);
    }
    
    public static Property convertRDFNodeToProperty(RDFNode node) {
        if (node.isResource()) {
            return (Property) node.as(Property.class);
        } else {
            throw new IllegalArgumentException("Given RDFNode is not a Property");
        }
    }
}
