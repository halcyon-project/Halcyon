/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.ethereal;

import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.impl.ModelCom;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.wicket.Component;
import org.apache.wicket.model.ChainingModel;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.rdf.model.Resource;

public abstract class AbstractNodeModel<T> extends ChainingModel<T> {
    private static final long serialVersionUID = 1L;

    public AbstractNodeModel(final Object modelObject) {
	super(modelObject);
    }
    
    public static class Blah<E> {
        public E createme(Class<E> clazz) {
            try {
                return clazz.newInstance();
            } catch (InstantiationException ex) {
                Logger.getLogger(AbstractNodeModel.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IllegalAccessException ex) {
                Logger.getLogger(AbstractNodeModel.class.getName()).log(Level.SEVERE, null, ex);
            }
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getObject() {        
        final String expression = propertyExpression();
        final Object target = getInnermostModelOrObject();
        System.out.println(target.getClass().getTypeName()+"  public T getObject() "+expression+"    ");
        if (target instanceof ModelCom) {
            Model m = (Model) target;
            Statement ss = m.getRequiredProperty(m.createResource(getNode().toString()), m.createProperty(m.getNsPrefixURI(propertyExpression())));
            String now = ss.getObject().asLiteral().getString();
            T ha = (T) now;
            Resource x;
            
            
            System.out.println("CLASS: "+ha.getClass().toString());
            return (T) now;
        }
        throw new UnsupportedOperationException("AbstractNodeModel : getObject: Can't handle this object type.  Sorry sibling.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setObject(T object) {
        System.out.println("setObject  : "+object);
        final Object target = getInnermostModelOrObject();
        if (target instanceof ModelCom) {
            Model m = (Model) target;
            Statement before = m.getRequiredProperty(m.createResource(getNode().toString()), m.createProperty(m.getNsPrefixURI(propertyExpression())));
            Statement after = m.createLiteralStatement(m.createResource(getNode().toString()), m.createProperty(m.getNsPrefixURI(propertyExpression())),object);
            m.remove(before);
            m.add(after);
            RDFDataMgr.write(System.out, m, RDFFormat.TURTLE_PRETTY);
        } else {
            throw new UnsupportedOperationException("AbstractNodeModel : setObject: Can't handle this object type.  Sorry sibling.");
        }
    }
    

    
    protected abstract String propertyExpression();
    protected abstract Component getComponent();
    protected abstract Node getNode();
}