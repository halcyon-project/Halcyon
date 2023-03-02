/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.ethereal;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.PropertyNotFoundException;
import org.apache.wicket.Component;
import org.apache.wicket.model.ChainingModel;

public abstract class AbstractxNodeModel<T> extends ChainingModel<T> {
    private static final long serialVersionUID = 1L;

    public AbstractxNodeModel(final Object modelObject) {
	super(modelObject);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getObject() {        
        final Object target = getInnermostModelOrObject();
        if (target instanceof xNode xn) {
            Model m = xn.getModel();
            Statement ss = getProperty(m, m.createResource(getNode().getNode().toString()),m.createProperty(m.getNsPrefixURI(propertyExpression())));
            String now = ss.getObject().asLiteral().getString();
            return (T) now;
        }
        throw new UnsupportedOperationException("AbstractxNodeModel : getObject: Can't handle this object type.  Sorry sibling.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setObject(T object) {
        //System.out.println("setObject  : "+object);
        final Object target = getInnermostModelOrObject();
        if (target instanceof xNode xn) {
            Model m = xn.getModel();
            Resource r = m.createResource(getNode().getNode().toString());
            Property p = m.createProperty(m.getNsPrefixURI(propertyExpression()));
            Statement before = getProperty(m,r,p);
            Statement after = m.createLiteralStatement(r, p, object);
            m.remove(before);
            m.add(after);
            //RDFDataMgr.write(System.out, m, RDFFormat.TURTLE_PRETTY);
        } else {
            throw new UnsupportedOperationException("AbstractxNodeModel : setObject: Can't handle this object type.  Sorry sibling.");
        }
    }
    
    public Statement getProperty(Model m, Resource r, Property p) {
        //System.out.println("AbstractxNodeModel getProperty : "+p.getURI());
        Statement ss;
        try {
            ss = m.getRequiredProperty(r, p);
        } catch (PropertyNotFoundException e) {
            System.out.println("NOT DEFINED DEFAULT FALSE");
            ss = m.createLiteralStatement(r, p, false);
            m.add(ss);
        }
        return ss;
    }

    protected abstract String propertyExpression();
    protected abstract Component getComponent();
    protected abstract xNode getNode();
}