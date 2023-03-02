/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.ethereal;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.wicket.markup.html.form.CheckBox;

/**
 *
 * @author erich
 */
public class RDFCheckBox extends CheckBox {
    private Triple triple;
    
    public RDFCheckBox(String id, Resource s, Property p) {
	super(id, null);
        this.triple = new Triple(s.asNode(), p.asNode(), Node.ANY);
    }
    
    public Triple getTriple() {
        return triple;
    }
    
    public void setTriple(Triple t) {
        triple = t;
    }
}
