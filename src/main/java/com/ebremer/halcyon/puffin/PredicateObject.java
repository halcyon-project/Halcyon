package com.ebremer.halcyon.puffin;

import com.ebremer.ethereal.RDFDetachableModel;
import java.util.HashSet;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Statement;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;

/**
 *
 * @author erich
 */
public class PredicateObject extends Panel implements IMarkupResourceStreamProvider {
    private Triple triple;
    private final Label label;

    public PredicateObject(String id, RDFDetachableModel mod, Statement s, HShapes ls, String messages, Node shape, SHACLForm form) {
        super(id);
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
        Component cc = (new RDFComponent("object", mod, s.asTriple(),dt)).getComponent();
        //cc.add(AttributeModifier.replace("style", "margin-left: ;"));
        divobject.add(cc);
        divstatus.add(status);
        AjaxButton button = new AjaxButton("deletethis") {
            @Override
            public void onSubmit(AjaxRequestTarget target) {
                Component parent = form.getParent();
                mod.getObject().remove(Tools.asStatement(mod.getObject(), triple));
                SHACLForm newsf = new SHACLForm(form.getId(), mod, mod.getObject().createResource(triple.getSubject().getURI()), shape);
                form.replaceWith(newsf);
                target.add(parent);
            }
        };
        divdelete.add(button);
    }
    
    public void setLabelVisible(boolean visible) {
        label.setVisible(visible);
    }

    @Override
    public IResourceStream getMarkupResourceStream(MarkupContainer mc, Class<?> type) {
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