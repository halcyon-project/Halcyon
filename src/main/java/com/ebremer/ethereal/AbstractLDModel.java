package com.ebremer.ethereal;

import com.ebremer.halcyon.wicket.DatabaseLocator;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.impl.ModelCom;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.wicket.Component;
import org.apache.wicket.model.ChainingModel;



public abstract class AbstractLDModel<T> extends ChainingModel<T> {
    private static final long serialVersionUID = 1L;

    public AbstractLDModel(final Object modelObject) {
	super(modelObject);
    }

    @Override
    public T getObject() {        
        Dataset ds = DatabaseLocator.getDatabase().getDataset();
        ds.begin(ReadWrite.READ);
        final String expression = propertyExpression();
        System.out.println("public T getObject() "+expression);
        final Object target = getInnermostModelOrObject();
        System.out.println("TARGET MAESTRO : "+target.getClass().toString());
        if (target instanceof ModelCom) {
            Model m = (Model) target;
            Triple triple = tripleExpression();
            Statement ss = m.getRequiredProperty(m.asRDFNode(triple.getSubject()).asResource(), m.createProperty(triple.getPredicate().getURI()));
            String now = ss.getObject().asLiteral().getString();
            Component c = getComponent();
            RDFTextField d = (RDFTextField) c;
            d.setTriple(ss.asTriple());
            ds.end();
            return (T) now;
        }
        throw new UnsupportedOperationException("AbstractRDF2Model : getObject: Can't handle this object type.  Sorry sibling.");
    }

    @Override
    public void setObject(T object) {
        Dataset ds = DatabaseLocator.getDatabase().getDataset();
        ds.begin(ReadWrite.WRITE);
        System.out.println("setObject  : "+object);
        final Object target = getInnermostModelOrObject();
        if (target instanceof ModelCom) {
            Model m = (Model) target;
            Triple triple = tripleExpression();
            Statement before = m.asStatement(triple);
            Node oo = NodeFactory.createLiteral((String) object);
            Statement after = m.asStatement(new Triple(triple.getSubject(),triple.getPredicate(),oo));
            m.remove(before);
            m.add(after);
            RDFDataMgr.write(System.out, m, RDFFormat.TURTLE_PRETTY);
            ds.commit();
            ds.end();
        } else {
            throw new UnsupportedOperationException("AbstractRDF2Model : setObject: Can't handle this object type.  Sorry sibling.");
        }
    }
    
    protected abstract String propertyExpression();
    protected abstract Triple tripleExpression();     
    protected abstract Component getComponent();
}