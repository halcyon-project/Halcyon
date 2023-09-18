package com.ebremer.halcyon.puffin;

import java.math.BigInteger;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.wicket.model.IModel;

/**
 *
 * @author erich
 */
public class ModelStatement implements IModel {
    private final DetachableStatement detachablestatement;

    public ModelStatement(Statement statement) {
        this.detachablestatement = new DetachableStatement(statement);
    }

    @Override
    public Object getObject() {
        RDFNode node = detachablestatement.getObject().getObject();
        if (node.isLiteral()) {
            Literal ha = node.asLiteral();
            Class clazz = ha.getDatatype().getJavaClass();
            if (clazz == Integer.class) {
                return ha.getInt();
            } else if (clazz == Float.class) {
                return ha.getFloat();
            } else if (clazz == String.class) {
                return ha.getString();
            }  else if (clazz == BigInteger.class) {
                if (ha.getLong()>Integer.MAX_VALUE) {
                    return ha.getInt();
                }
                return ha.getLong();
            } else {
                throw new Error("Cant handle this");
            }
        } else if (node.isURIResource()) {
            return node.asResource().getURI();
        }
        return "ERROR";
    }

    @Override
    public void setObject(Object object) {
        RDFNode o;
        switch (object) {
            case Float value -> o = ResourceFactory.createTypedLiteral(value);
            case Integer value -> o = ResourceFactory.createTypedLiteral(value);
            case String value -> o = ResourceFactory.createTypedLiteral(value);
            case Resource value -> o = value;
            default -> throw new Error("I CAN HANDLE THIS !!  --> "+object);
        }
        Statement statement = detachablestatement.getObject();
        statement.changeObject(o);
    }    
}
