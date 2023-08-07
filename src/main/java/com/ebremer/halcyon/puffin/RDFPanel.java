package com.ebremer.halcyon.puffin;

import com.ebremer.ethereal.RDFDetachableModel;
import com.ebremer.ns.HAL;
import java.util.HashSet;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
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
    private Triple triple;
    private final Label label;
    private boolean oform = false;

    public RDFPanel(String id, RDFDetachableModel mod, Statement s, HShapes ls, String messages, Node shape, SHACLForm form) {
        super(id);
        System.out.println("PredicateObject ---> "+s.asTriple());
        this.triple = s.asTriple();
        String PredicateLabel = s.getPredicate().getLocalName();
        label = new Label("predicate", Model.of(PredicateLabel));
        HashSet<Node> dt = ls.getDataTypes(shape,s.getPredicate().asResource());
        WebMarkupContainer divlabel = new WebMarkupContainer("divlabel");
        divlabel.add(AttributeModifier.replace("style", "width: 150px; display: inline-block;"));
        WebMarkupContainer divobject = new WebMarkupContainer("divobject");
        divobject.setOutputMarkupId(true);
        divobject.add(AttributeModifier.replace("style", "display: inline-block;"));
        WebMarkupContainer divdelete = new WebMarkupContainer("divdelete"); 
        divdelete.add(AttributeModifier.replace("style", "display: inline-block;"));
        WebMarkupContainer divstatus = new WebMarkupContainer("divstatus");
        divstatus.add(AttributeModifier.replace("style", "display: inline-block;"));
        add(divlabel);
        add(divobject);
        add(divdelete);
        add(divstatus);
        Label status = new Label("status", Model.of(messages));
        if (messages.isEmpty()) {
            status.setVisible(false);
        } else {
            status.setVisible(true);
        }
        divlabel.add(label);
        //cc.add(AttributeModifier.replace("style", "margin-left: ;"));
        divstatus.add(status);
        AjaxButton button = new AjaxButton("deletethis") {
            @Override
            public void onSubmit(AjaxRequestTarget target) {
                Component parent = form.getParent();
                mod.getObject().remove(Tools.asStatement(mod.getObject(), triple));
                SHACLForm newsf;
                if (triple.getSubject().isBlank()) {
                    newsf = new SHACLForm(form.getId(), mod, mod.getObject().createResource(AnonId.create(triple.getSubject().getBlankNodeLabel())), shape);
                } else {
                    newsf = new SHACLForm(form.getId(), mod, mod.getObject().createResource(triple.getSubject().getURI()), shape);
                }
                form.replaceWith(newsf);
                target.add(parent);
            }
        };
        divdelete.add(button);
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
                TextField<String> textField = new TextField<>("object", new WicketTriple(mod, triple), Integer.class);
                textField.add(new AttributeAppender("style", "width:500px;"));
                divobject.add(textField);
            } else if (dtx.getJavaClass() == Float.class) {
                TextField<String> textField = new TextField<>("object", new WicketTriple(mod, triple), Float.class);
                textField.add(new AttributeAppender("style", "width:500px;"));
                divobject.add(textField);
            } else if (dtx.getJavaClass() == Double.class) {
                TextField<String> textField = new TextField<>("object", new WicketTriple(mod, triple), Double.class);
                textField.add(new AttributeAppender("style", "width:500px;"));
                divobject.add(textField);
            } else if (dtx.getJavaClass() == String.class) {
                TextField<String> textField = new TextField<>("object", new WicketTriple(mod, triple), String.class);
                textField.add(new AttributeAppender("style", "width:500px;"));
                divobject.add(textField);
            } else {
                throw new Error("Can't handle this!!! "+dtx.getJavaClass().toGenericString());
            }   
        } else if (node.isURIResource()) {
                        System.out.println("HASH1");
                TextField<String> textField = new TextField<>("object", new WicketTriple(mod, triple), Resource.class);
                textField.add(new AttributeAppender("style", "width:500px;"));
                divobject.add(textField);
        } else if (node.isAnon()) {
            System.out.println("HASH2222");
            oform = true;
            SHACLForm formx = new SHACLForm("object", mod, mod.getObject().createResource(new AnonId(triple.getObject().getBlankNodeLabel())), HAL.AnnotationClassShape.asNode());
            divobject.add(formx);
        } else {
            throw new Error("RDFComponent Unhandled ---> "+node);
        }
    }
    
    public void setLabelVisible(boolean visible) {
        label.setVisible(visible);
    }

    @Override
    public IResourceStream getMarkupResourceStream(MarkupContainer mc, Class<?> type) {
        System.out.println("GENTEXT!!! "+oform+"  "+triple);
        if (oform) {
            System.out.println("PREDICATEISAFORM!!!!!!!!!!!!");
            return new StringResourceStream("""
                <html><body>
                <wicket:panel>
                    <div wicket:id="divlabel"><label wicket:id="predicate"/></div>
                    <div wicket:id="divobject"><div wicket:id="object"></div></div>
                    <div wicket:id="divdelete"><button wicket:id="deletethis">Delete</button></div>
                    <div wicket:id="divstatus"><label wicket:id="status"/></div>
                </wicket:panel>
                </body></html>
                """);            
        }
        return new StringResourceStream("""
            <html><body>
            <wicket:panel>
                <div wicket:id="divlabel"><label wicket:id="predicate"/></div>
                <div wicket:id="divobject"><input type="text" wicket:id="object"/></div>
                <div wicket:id="divdelete"><button wicket:id="deletethis">Delete</button></div>
                <div wicket:id="divstatus"><label wicket:id="status"/></div>
            </wicket:panel>
            </body></html>
            """);
    }
}
