/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.ethereal;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.impl.ModelCom;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.wicket.Component;
import org.apache.wicket.model.ChainingModel;



public abstract class AbstractSolutionModel<T> extends ChainingModel<T> {
    private static final long serialVersionUID = 1L;

    public AbstractSolutionModel(final Object modelObject) {
	super(modelObject);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getObject() {
        System.out.println("SOLUTION : getObject()");
        //Dataset ds = DatabaseLocator.getDatabase().getDataset();
        //ds.begin(ReadWrite.READ);
        final String expression = propertyExpression();
        System.out.println("public T getObject() EXPRESSION "+expression);
        final Object target = getInnermostModelOrObject();
        System.out.println("TARGET MAESTRO : "+target.getClass().toString());
        if (target instanceof Solution) {
            return (T) "NONE";
        }
        throw new UnsupportedOperationException("AbstractSolutionModel : getObject: Can't handle this object type.  Sorry sibling.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setObject(T object) {
        System.out.println("setObject  : "+object);
        final Object target = getInnermostModelOrObject();
        if (target instanceof ModelCom) {
            Model m = (Model) target;
            Triple triple = tripleExpression();
            Statement before = m.asStatement(triple);
            Node oo = NodeFactory.createLiteral((String) object);
            Statement after = m.asStatement(Triple.create(triple.getSubject(),triple.getPredicate(),oo));
            m.remove(before);
            m.add(after);
            RDFDataMgr.write(System.out, m, RDFFormat.TURTLE_PRETTY);
        } else {
            throw new UnsupportedOperationException("AbstractRDF2Model : setObject: Can't handle this object type.  Sorry sibling.");
        }
    }
    
    protected abstract String propertyExpression();
    protected abstract Triple tripleExpression();     
    protected abstract Component getComponent();
}