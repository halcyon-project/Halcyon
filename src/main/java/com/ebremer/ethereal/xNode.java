/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.ethereal;

import com.ebremer.ns.HAL;
import java.io.Serializable;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PropertyNotFoundException;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class xNode implements Serializable {
    private final Node node;
    private final RDFDetachableModel context;
    
    public xNode(Node node, RDFDetachableModel context) {
        this.node = node;
        this.context = context;
    }

    public Node getNode() {
        return node;
    }
    
    public String getURI() {
        return node.getURI();
    }
    
    public xNode getParent() {
        try {
            Resource p = getModel().getRequiredProperty(getResource(), SchemaDO.isPartOf).getObject().asResource();
            xNode parent = new xNode(p.asNode(), getContent());
            return parent;
        } catch(PropertyNotFoundException e) {
            return null;
        }
    }
    
    public boolean isSelected() {
        try {
            boolean isSelected = getModel().getRequiredProperty(getResource(), HAL.isSelected).getObject().asLiteral().getBoolean();
            System.out.println(getURI()+" IS selected");
            return isSelected;
        } catch (PropertyNotFoundException e)  {
            System.out.println(getURI()+" is not selected");
            Statement ss = getModel().createLiteralStatement(getResource(), HAL.isSelected, false);
            getModel().add(ss);
            return false;
        }
    }
    
    public RDFDetachableModel getContent() {
        return context;
    }
    
    public Resource getResource() {
        return context.getObject().asRDFNode(node).asResource();
    }
    
    public Model getModel() {
        return context.load();
    }
    
    @Override
    public String toString() {
        return node.toString();
    }
}
