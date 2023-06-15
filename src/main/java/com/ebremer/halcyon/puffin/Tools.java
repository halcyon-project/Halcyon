package com.ebremer.halcyon.puffin;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

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
            default -> o = null;
        }
        if (o==null) {
            System.out.println("Deal with this --> "+newobj.getClass().toGenericString());
        }
        return Triple.create(s, p, o);
    }
    
    public static Statement newStatement(Model m, Triple triple, Object newobj) {
        return asStatement(m, newTriple(m,triple,newobj));
    }
    
    public static Statement asStatement(Model m, Triple triple) {
        Resource s;
        Property p;
        Node node = triple.getObject();
        if (triple.getSubject().isBlank()) {
            s = m.createResource(new AnonId(triple.getSubject().getBlankNodeId().getLabelString()));
        } else {
            s = m.createResource(triple.getSubject().getURI());
        }
        p = m.createProperty(triple.getPredicate().getURI());
        RDFNode rdfNode;
        if (node.isURI()) {
            rdfNode = m.createResource(node.getURI());
        } else if (node.isBlank()) {
            rdfNode = m.createResource(new AnonId(node.getBlankNodeId().getLabelString()));
        } else if (node.isLiteral()) {
            String lex = node.getLiteralLexicalForm();
            String lang = node.getLiteralLanguage();
            String uri = node.getLiteralDatatypeURI();                
            if (uri != null) {
                RDFDatatype datatype = TypeMapper.getInstance().getSafeTypeByName(uri);
                rdfNode = m.createTypedLiteral(lex, datatype);
            } else if (!lang.isEmpty()) {
                rdfNode = m.createLiteral(lex, lang);
            } else {
                rdfNode = m.createLiteral(lex);
            }
        } else {
            throw new IllegalArgumentException("Node is not a URI, blank node, or literal");
        }
        return m.createStatement(s, p, rdfNode);
    }
}
