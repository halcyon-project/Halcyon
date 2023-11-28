package com.ebremer.ethereal;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.wicket.markup.html.form.TextField;

/**
 *
 * @author erich
 * @param <T>
 */
public class RDFTextField<T> extends TextField<T> {
    private Triple triple;
    
    public RDFTextField(String id, Resource s, Property p) {
	super(id, null, null);
        this.triple = Triple.create(s.asNode(), p.asNode(), Node.ANY);
    }
    
    public Triple getTriple() {
        return triple;
    }
    
    public void setTriple(Triple t) {
        triple = t;
    }
}