package com.ebremer.halcyon.puffin;

import com.ebremer.ethereal.RDFDetachableModel;
import com.ebremer.ns.HAL;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;

/**
 *
 * @author erich
 */
public class RDFPanel extends Panel implements IMarkupResourceStreamProvider {
    private final Triple triple;

    public RDFPanel(String id, RDFDetachableModel mod, Statement s, HShapes ls, String messages, Node shape, SHACLForm form) {
        super(id);
        this.triple = s.asTriple();
        WebMarkupContainer divobject = new WebMarkupContainer("divobject");
        divobject.setOutputMarkupId(true);
        divobject.add(AttributeModifier.replace("style", "display: inline-block;"));
        //WebMarkupContainer divstatus = new WebMarkupContainer("divstatus");
        //divstatus.add(AttributeModifier.replace("style", "display: inline-block;"));
        add(divobject);
        //add(divstatus);
        Label status = new Label("status", Model.of(messages));
        if (messages.isEmpty()) {
            status.setVisible(false);
        } else {
            status.setVisible(true);
        }
        //cc.add(AttributeModifier.replace("style", "margin-left: ;"));
        //divstatus.add(status);
        org.apache.jena.rdf.model.Model k = mod.getObject();
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
            RDFDatatype dtx = node.asLiteral().getDatatype();
            if (dtx.getJavaClass() == Integer.class) {
                WebMarkupContainer subform = new WebMarkupContainer("subform");
                TextField<String> textField = new TextField<>("subobject", new WicketTriple(mod, triple), Integer.class);
                textField.add(new AttributeAppender("style", "width:500px;"));
                subform.setVisible(false);
                divobject.add(textField);
                divobject.add(subform);
            } else if (dtx.getJavaClass() == Float.class) {
                WebMarkupContainer subform = new WebMarkupContainer("subform");
                TextField<String> textField = new TextField<>("subobject", new WicketTriple(mod, triple), Float.class);
                textField.add(new AttributeAppender("style", "width:500px;"));
                subform.setVisible(false);
                divobject.add(textField);
                divobject.add(subform);
            } else if (dtx.getJavaClass() == Double.class) {
                WebMarkupContainer subform = new WebMarkupContainer("subform");
                TextField<String> textField = new TextField<>("subobject", new WicketTriple(mod, triple), Double.class);
                textField.add(new AttributeAppender("style", "width:500px;"));
                subform.setVisible(false);
                divobject.add(textField);
                divobject.add(subform);
            } else if (dtx.getJavaClass() == String.class) {
                WebMarkupContainer subform = new WebMarkupContainer("subform");
                TextField<String> textField = new TextField<>("subobject", new WicketTriple(mod, triple), String.class);
                textField.add(new AttributeAppender("style", "width:500px;"));
                //textField.setEnabled(false);
                subform.setVisible(false);
                divobject.add(textField);
                divobject.add(subform);
            } else {
                throw new Error("Can't handle this!!! "+dtx.getJavaClass().toGenericString());
            }   
        } else if (node.isURIResource()) {
            WebMarkupContainer subform = new WebMarkupContainer("subform");
            TextField<String> textField = new TextField<>("subobject", new WicketTriple(mod, triple), Resource.class);
            textField.add(new AttributeAppender("style", "width:500px;"));
            divobject.add(textField);
            subform.setVisible(false);
            divobject.add(subform);
        } else if (node.isAnon()) {
            SHACLForm formx = new SHACLForm("subform", mod, mod.getObject().createResource(new AnonId(triple.getObject().getBlankNodeLabel())), HAL.AnnotationClassShape.asNode());
            divobject.add(formx);
            TextField<String> textField = new TextField<>("subobject", Model.of("YAY"));
            divobject.add(textField);
            textField.setVisible(false);
        } else {
            throw new Error("RDFComponent Unhandled ---> "+node);
        }
    }

    @Override
    public IResourceStream getMarkupResourceStream(MarkupContainer mc, Class<?> type) {
        return new StringResourceStream("""
            <html><body>
            <wicket:panel>
                <div wicket:id="divobject"><div wicket:id="subform"></div><input type="text" wicket:id="subobject"/></div>
            </wicket:panel>
            </body></html>
            """);
    }
}
