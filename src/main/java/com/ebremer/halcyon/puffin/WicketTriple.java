package com.ebremer.halcyon.puffin;

import com.ebremer.ethereal.RDFDetachableModel;
import java.math.BigInteger;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.wicket.model.IModel;


/**
 *
 * @author erich
 */
public class WicketTriple implements IModel {
    private static final long serialVersionUID = 1L;
    private final RDFDetachableModel model;
    private Triple triple;

    public WicketTriple(final RDFDetachableModel model, final Triple triple) {
        this.model = model;
        this.triple = triple;
        //System.out.println("WicketTriple --> "+triple);
    }

    @Override
    public Object getObject() {
        Model k = model.getObject();
        Resource rr;
        if (triple.getSubject().isBlank()) {
            rr = k.createResource(new AnonId(triple.getSubject().getBlankNodeId().getLabelString()));
        } else if (triple.getSubject().isURI()) {
            rr = k.createResource(triple.getSubject().getURI());
        } else {
            throw new Error("HAHAH");
        }
        RDFNode node = k.asRDFNode(triple.getObject());
        if (triple.getObject().isLiteral()) {
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
        Model m = model.getObject();
        m.remove(Tools.asStatement(m, triple));
        triple = Tools.newTriple(m, triple, object);
        m.add(Tools.asStatement(m, triple));
    }    
}
