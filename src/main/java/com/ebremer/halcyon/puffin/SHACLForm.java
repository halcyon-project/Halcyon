package com.ebremer.halcyon.puffin;

import com.ebremer.ethereal.RDFDetachableModel;
import com.ebremer.ns.DASH;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;

public class SHACLForm extends Panel implements IMarkupResourceStreamProvider {
    private final DropDownChoice<Bundle> ddc;
    private Node subject;

    public SHACLForm(String id, RDFDetachableModel mod, Resource r, Node shape) {
        super(id);
        this.subject = r.asNode();
        Form form = new Form("form", mod);
        HShapes ls = new HShapes();       
        RepeatingView parentRepeatingView = new RepeatingView("predicateObjectRepeatingView");
        form.add(parentRepeatingView);
        ResultSet rs = ls.getPredicateFormElements(r,shape);
        WebMarkupContainer parentItem;
        while (rs.hasNext()) {
            int c = 0;
            QuerySolution qs = rs.next();
            System.out.println("PROPERTY 1 ---> "+qs);
            Property predicate = Tools.convertRDFNodeToProperty(qs.get("predicate"));
            parentItem = new WebMarkupContainer(parentRepeatingView.newChildId());
            Label predicateGroupMessages;
            if (qs.contains("pmessages")) {
                predicateGroupMessages = new Label("predicatemessages", qs.get("pmessages").asLiteral().getString());
                parentItem.add(AttributeModifier.replace("style", "background-color: #FFAAAA;"));
                predicateGroupMessages.setVisible(true);
            } else {
                predicateGroupMessages = new Label("predicatemessages", "****");
                predicateGroupMessages.setVisible(false);
            }
            Label predicateGroupLabel = new Label("predicatename", qs.get("predicate").asNode().getLocalName());
            predicateGroupLabel.setVisible(false);  //disable for now
            parentRepeatingView.add(parentItem);
            parentItem.add(predicateGroupLabel);
            parentItem.add(predicateGroupMessages);
            RepeatingView childRepeatingView = new RepeatingView("childRepeatingView");
            parentItem.add(childRepeatingView);
            if (qs.contains("viewers")) {
                Node v = NodeFactory.createURI(qs.get("viewers").asLiteral().getString());
                if (v.equals(DASH.ValueTableViewer.asNode())) {
                    GridPanel grid;
                    if (qs.contains("subshapes")) {
                        String subshape = qs.get("subshapes").asLiteral().getString().split(",")[0];
                        Node na = NodeFactory.createURI(subshape);
                        grid = new GridPanel(childRepeatingView.newChildId(), r, predicate, na);
                    } else {
                        grid = new GridPanel(childRepeatingView.newChildId(), r, predicate, null);
                    }
                    childRepeatingView.add(grid);
                }
            } else {
                ResultSet rrs = ls.getStatementFormElements(r,predicate,shape);
                while (rrs.hasNext()) {
                    QuerySolution qqs = rrs.next();
                    Statement ma = r.getModel().asStatement(Triple.create(r.asNode(), predicate.asNode(), qqs.get("object").asNode()));
                    String messages = "";
                    if (qqs.contains("messages")) {
                        messages = qqs.get("messages").asLiteral().getString();
                    }
                    System.out.println("QQS ----> "+qqs);
                    PredicateObject predicateObject = new PredicateObject(childRepeatingView.newChildId(), mod, ma, ls, messages, shape, this, qqs);
                    if (c>0) {
                        predicateObject.setLabelVisible(false);
                    }
                    childRepeatingView.add(predicateObject);
                    c++;
                }
            }
        }
        form.add(new AjaxButton("saveButton") {
            @Override
            public void onSubmit(AjaxRequestTarget target) {
                HShapes hshapes = new HShapes();
                hshapes.Validate(mod.getObject());
                Component parent = SHACLForm.this.getParent();
                if (subject.isBlank()) {
                    SHACLForm newsf = new SHACLForm(SHACLForm.this.getId(), mod, mod.getObject().createResource(AnonId.create(subject.getBlankNodeLabel())), shape);
                    SHACLForm.this.replaceWith(newsf);
                } else {
                    SHACLForm newsf = new SHACLForm(SHACLForm.this.getId(), mod, mod.getObject().createResource(subject.getURI()), shape);
                    SHACLForm.this.replaceWith(newsf);
                }
                target.add(parent);
            }}.setDefaultFormProcessing(true)
        );
        form.add(new AjaxButton("deleteButton") {
            @Override
            public void onSubmit(AjaxRequestTarget target) {
                System.out.println("delete this");
                //Component parent = SHACLForm.this.getParent();
                //SHACLForm newsf = new SHACLForm(SHACLForm.this.getId(), mod, mod.getObject().createResource(subject.getURI()), shape);
                //SHACLForm.this.replaceWith(newsf);
                //target.add(parent);
            }}.setDefaultFormProcessing(true)
        );
        form.add(new Button("resetButton") {
            @Override
            public void onSubmit() {
                // setResponsePage(ResourceViewer.class);
            }}.setDefaultFormProcessing(false)
        );
        add(form);
        ddc = new DropDownChoice<>("predicates", new Model<Bundle>(), ls.getStatsAndPredicates(shape,r), new BundleRender());
        ddc.setNullValid(true);
        ddc.add(new OnChangeAjaxBehavior() {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                if (ddc.getModelObject()!=null) {
                    Component parent = SHACLForm.this.getParent();
                    HShapes hshapes = new HShapes();
                    Resource r = mod.getObject().createResource(ddc.getModelObject().getNode().getURI());
                    if (subject.isBlank()) {
                        hshapes.createProperty(shape, mod.getObject(), mod.getObject().createResource(AnonId.create(subject.getBlankNodeLabel())), r);
                    } else {
                        hshapes.createProperty(shape, mod.getObject(), mod.getObject().createResource(subject.getURI()), r);
                    }
                    System.out.println("ADDED DATA =======================================");
                    RDFDataMgr.write(System.out, mod.getObject(), Lang.TURTLE);
                    System.out.println("^^^^^^^^^^^^^^^^^^^  ============================");
                    SHACLForm newsf;
                    if (subject.isBlank()) {
                        newsf = new SHACLForm(SHACLForm.this.getId(), mod, mod.getObject().createResource(AnonId.create(subject.getBlankNodeLabel())), shape);
                    } else {
                        newsf = new SHACLForm(SHACLForm.this.getId(), mod, mod.getObject().createResource(subject.getURI()), shape);
                    }
                    SHACLForm.this.replaceWith(newsf);
                    target.add(parent);
                }
            }
        });
        form.add(ddc);
    }
    
    @Override
    public IResourceStream getMarkupResourceStream(MarkupContainer container, Class<?> containerClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><wicket:panel><form wicket:id=\"form\">")
            .append("<input type=\"submit\" wicket:id=\"saveButton\" value=\"save\" />")
            .append("<input type=\"submit\" wicket:id=\"deleteButton\" value=\"delete\" />")
            .append("<input type=\"submit\" wicket:id=\"resetButton\" value=\"reset\" />")
            .append("<select wicket:id=\"predicates\">Predicates</select>")
            .append("<div wicket:id=\"predicateObjectRepeatingView\"><label wicket:id=\"predicatename\"/><label wicket:id=\"predicatemessages\"/><div wicket:id=\"childRepeatingView\"></div></div>")
            .append("</form></wicket:panel></body></html>");
        return new StringResourceStream(sb.toString());
    }
}
