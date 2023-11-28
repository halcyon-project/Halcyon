package com.ebremer.halcyon.puffin;

import com.ebremer.ns.DASH;
import com.ebremer.ns.HAL;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.Model;
import org.wicketstuff.select2.Select2Choice;

/**
 *
 * @author erich
 */
public class ComponentBuilder {
    
    public static Component BuildTextField(String id, Statement s) {
        ModelStatement modelstatement = new ModelStatement(s);
        RDFNode node = s.getObject();
        if (node.isLiteral()) {
            RDFDatatype dtx = node.asLiteral().getDatatype();
            TextField<String> textField;
            if (dtx.getJavaClass() == Integer.class) {
                textField = new TextField<>(id, modelstatement, Integer.class);
                textField.setOutputMarkupId(true);
            } else if (dtx.getJavaClass() == Float.class) {
                textField = new TextField<>(id, modelstatement, Float.class);
                textField.setOutputMarkupId(true);
            } else if (dtx.getJavaClass() == Double.class) {
                textField = new TextField<>(id, modelstatement, Double.class);
                textField.setOutputMarkupId(true);
            } else if (dtx.getJavaClass() == String.class) {
                textField = new TextField<>(id, modelstatement, String.class);
                textField.setOutputMarkupId(true);
            } else {
                throw new Error("Can't handle this!!! "+dtx.getJavaClass().toGenericString());
            }
            return textField;
        } else if (node.isURIResource()) {
            TextField<String> textField = new TextField<>(id, modelstatement, Resource.class);
            textField.add(new AttributeAppender("style", "width:500px;"));
            textField.add(AttributeModifier.replace("maxlength", "255"));
            textField.setOutputMarkupId(true);
            return textField;
        } else if (node.isAnon()) {
            return new SHACLForm(id, s.getModel().createResource(new AnonId(s.getObject().asNode().getBlankNodeLabel())), HAL.AnnotationClassShape.asNode(), null);
        } else {
            throw new Error("RDFComponent Unhandled ---> "+node);
        }
    }
    
    public static Component BuildComponent(String id, Statement s, Predicate p) {
        if (p!=null) {
            if (p.editor()==null) {
                return BuildTextField(id,s);
            } else {
                if (p.editor().equals(HAL.ColorEditor.asNode())) {
                    return new ColorPickerEditor(id, RDFStatement.of(s), String.class);
                } else if (p.editor().equals(HAL.SNOMEDEditor.asNode())) {
                    Select2Choice sc = new Select2Choice(id, RDFStatement.of(s), new SNOMEDProvider());
                    sc.getSettings().setMinimumInputLength(3);
                    sc.getSettings().setWidth("750px");
                    return sc;
                }
            }
        }
        return BuildTextField(id,s);
    }
    
    public static Component BuildComponent(String id, Statement s, Node shape) {
        HShapes hshapes = new HShapes();
        Predicate p = hshapes.getPredicate(s.getPredicate(), shape);
        return BuildComponent(id, s, p);
    }
    
    public static Component BuildComponentPanel(String id, Statement s, Predicate p) {
        Component c = BuildComponent("comp", s, p);
        if (c instanceof TextField) {
            return new InputPanel(id,c);
        } else if (c instanceof Select2Choice) {
            return new SelectPanel(id,c);
        } else if (c instanceof SHACLForm) {
            return BuildComponent(id, s, p);  // need to make this more efficient.
        }
        throw new Error("Can panel wrap ----> "+c.getClass().toGenericString());
    }
    
    public static Component BuildLabelField(String id, Statement s) {
        ModelStatement modelstatement = new ModelStatement(s);
        RDFNode node = s.getObject();
        if (node.isLiteral()) {
            RDFDatatype dtx = node.asLiteral().getDatatype();
            Label textField;
            if (dtx.getJavaClass() == Integer.class) {
                textField = new Label(id, modelstatement);
            } else if (dtx.getJavaClass() == Float.class) {
                textField = new Label(id, modelstatement);
            } else if (dtx.getJavaClass() == Double.class) {
                textField = new Label(id, modelstatement);
            } else if (dtx.getJavaClass() == String.class) {
                textField = new Label(id, modelstatement);
                textField.add(new AttributeAppender("style", "width:500px;"));
            } else {
                throw new Error("Can't handle this!!! "+dtx.getJavaClass().toGenericString());
            }
            return textField;
        } else if (node.isURIResource()) {
            Label textField = new Label(id, modelstatement);
            textField.add(AttributeModifier.replace("size", "500px"));
            return textField;
        } else if (node.isAnon()) {
            Label textField = new Label(id, Model.of("YAY"));
            return textField;
        } else {
            throw new Error("RDFComponent Unhandled ---> "+node);
        }
    }
}
