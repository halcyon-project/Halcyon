package com.ebremer.halcyon.puffin;

import com.ebremer.ethereal.RDFDetachableModel;
import com.ebremer.ns.HAL;
import java.util.HashSet;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.wicket.Component;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.TextField;


/**
 *
 * @author erich
 */
public class RDFComponent {
    private static final long serialVersionUID = 1701L;
    private final RDFDetachableModel model;
    private final Triple triple;
    private Class clazz;
    private final String id;
    private final HashSet<Node> dtx;
    private final DataType datatype;
    
    public RDFComponent(String id, final RDFDetachableModel model, final Triple triple, HashSet<Node> dtx) {
        this.dtx = dtx;
        this.model = model;
        this.triple = triple;
        this.id = id;
        Model k = model.getObject();
        Resource rr;
        if (triple.getSubject().isBlank()) {
            rr = k.createResource(new AnonId(triple.getSubject().getBlankNodeId().getLabelString()));
        } else if (triple.getSubject().isURI()) {
            rr = k.createResource(triple.getSubject().getURI());
        } else {
            throw new Error("HAHAHX --> "+triple);
        }
        Property pp = k.createProperty(triple.getPredicate().getURI());
        RDFNode node = k.getProperty(rr,pp).getObject();
        if (node.isLiteral()) {
            RDFDatatype dt = node.asLiteral().getDatatype();
            if (dt.getJavaClass() == Integer.class) {
                clazz = Integer.class;
            } else if (dt.getJavaClass() == Float.class) {
                clazz = Float.class;
            } else if (dt.getJavaClass() == Double.class) {
                clazz = Double.class;
            } else if (dt.getJavaClass() == String.class) {
                clazz = String.class;
            } else {
                throw new Error("Can't handle this!!! "+clazz.toGenericString());
            }   
        } else if (node.isURIResource()) {
            clazz = Resource.class;
        } else if (node.isAnon()) {
            clazz = SHACLForm.class;
        } else {
            throw new Error("RDFComponent Unhandled ---> "+node);
        }
        datatype = DataType.fromJavaType(clazz);
    }
    
    public Component getComponent() {
        System.out.println("adding form element : "+clazz+" --> "+dtx);
        switch (datatype) {
            case BOOLEAN:
                CheckBox checkbox = new CheckBox(id, new WicketTriple(model, triple));
                return checkbox;
            case FORM:
                return new SHACLForm(id, model, model.getObject().createResource(new AnonId(triple.getObject().getBlankNodeLabel())), HAL.AnnotationClassShape.asNode());
            case STRING:
            case RESOURCE:
            default:
                TextField<String> textField = new TextField<>(id, new WicketTriple(model, triple), clazz);
                textField.add(new AttributeAppender("style", "width:500px;"));
                return textField;
        }
    }
}
