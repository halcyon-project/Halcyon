package com.ebremer.halcyon.puffin;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Statement;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.resource.ContextRelativeResource;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;

/**
 *
 * @author erich
 */
public class PredicateObject extends Panel implements IMarkupResourceStreamProvider {
    private final Triple triple;
    private final Label label;
    private final CommandNode cn;

    public PredicateObject(String id, Statement s, HShapes hs, String messages, Node shape, SHACLForm form, QuerySolution qs, CommandNode cn) {
        super(id);
        this.cn = cn;
        this.triple = s.asTriple();
        setOutputMarkupId(true);
        DetachableStatement stmt = new DetachableStatement(s);
        String PredicateLabel = s.getPredicate().getLocalName();
        label = new Label("predicate", Model.of(PredicateLabel));
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
        status.setVisible(!messages.isEmpty());
        divlabel.add(label);
        divstatus.add(status);
        AjaxSubmitLink deleteButton = new AjaxSubmitLink("deleteButton") {
            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                Component parent = form.getParent();
                stmt.getObject().getModel().remove(stmt.getObject().getModel().asStatement(triple));
                SHACLForm newsf;
                if (triple.getSubject().isBlank()) {
                    newsf = new SHACLForm(form.getId(), stmt.getObject().getModel().createResource(AnonId.create(triple.getSubject().getBlankNodeLabel())), shape, cn);
                } else {
                    newsf = new SHACLForm(form.getId(), stmt.getObject().getModel().createResource(triple.getSubject().getURI()), shape, cn);
                }
                form.replaceWith(newsf);
                target.add(parent);
            }
        };
        deleteButton.setDefaultFormProcessing(true);
        ContextRelativeResource ha = new ContextRelativeResource("images/minus.png");
        Image image = new Image("buttonImage", ha);
        image.add(AttributeModifier.replace("width", "25"));
        image.add(AttributeModifier.replace("height", "25"));
        deleteButton.add(image);
        divdelete.add(deleteButton);
        HShapes hshapes = new HShapes();
        Predicate p = hshapes.getPredicate(s.getPredicate(), shape);
        divobject.add(new RDFPanel("object", s, p));
    }
    
    public void setLabelVisible(boolean visible) {
        label.setVisible(visible);
    }

    @Override
    public IResourceStream getMarkupResourceStream(MarkupContainer mc, Class<?> type) {
        return new StringResourceStream("""
            <wicket:panel>
                <div style="display: flex; vertical-align: middle; height: 100%;">
                <div wicket:id="divlabel"><label wicket:id="predicate"/></div>
                <div wicket:id="divobject"><div wicket:id="object"></div></div>
                <div wicket:id="divdelete"><a wicket:id=\"deleteButton\"><img wicket:id=\"buttonImage\" src=\"\" alt=\"delete\"/></a></div>
                <div wicket:id="divstatus"><label wicket:id="status"/></div>
                </div>
            </wicket:panel>
            """);
    }
}
