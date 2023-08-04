package com.ebremer.halcyon.puffin;

import com.ebremer.ethereal.RDFDetachableModel;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
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
    private final RDFDetachableModel mod;

    public SHACLForm(String id, RDFDetachableModel mod, Resource r, Node shape) {
        super(id);
        this.subject = r.asNode();
        this.mod = mod;
        Form form = new Form("form", mod);
        HShapes ls = new HShapes();       
        RepeatingView parentRepeatingView = new RepeatingView("predicateObjectRepeatingView");
        form.add(parentRepeatingView);
        ResultSet rs = ls.getFormElements(r,shape);
        Node predicate = Node.ANY;
        WebMarkupContainer parentItem;
        RepeatingView childRepeatingView = null;
        int c = 0;
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            System.out.println("SOLUTION ---> "+qs);
            if (!predicate.equals(qs.get("predicate").asNode())) {
                c=0;
                predicate = qs.get("predicate").asNode();
                parentItem = new WebMarkupContainer(parentRepeatingView.newChildId());
                Label predicateGroupMessages;
                if (qs.contains("pmessages")) {
                    predicateGroupMessages = new Label("beef2", qs.get("pmessages").asLiteral().getString());
                    parentItem.add(AttributeModifier.replace("style", "background-color: #FFAAAA;"));
                    predicateGroupMessages.setVisible(true);
                } else {
                    predicateGroupMessages = new Label("beef2", "****");
                    predicateGroupMessages.setVisible(false);
                }
                Label predicateGroupLabel = new Label("beef", predicate.getLocalName());
                predicateGroupLabel.setVisible(false);  //disable for now
                parentRepeatingView.add(parentItem);
                parentItem.add(predicateGroupLabel);
                parentItem.add(predicateGroupMessages);
                childRepeatingView = new RepeatingView("childRepeatingView");
                parentItem.add(childRepeatingView);
            } else {
                c++;
            }
            Statement ma = Tools.asStatement(r.getModel(), Triple.create(r.asNode(), qs.get("predicate").asNode(), qs.get("object").asNode()));
            String messages ="";
            if (qs.contains("messages")) {
                messages = qs.get("messages").asLiteral().getString();
            }
            PredicateObject predicateObject = new PredicateObject(childRepeatingView.newChildId(), mod, ma, ls, messages, shape, this);
            if (c>0) {
                predicateObject.setLabelVisible(false);
            }
            childRepeatingView.add(predicateObject);
        }
        form.add(new AjaxButton("saveButton") {
            @Override
            public void onSubmit(AjaxRequestTarget target) {
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
                    hshapes.createProperty2(shape, mod.getObject(), mod.getObject().createResource(subject.getURI()), r);
                    System.out.println("ADDED DATA =======================================");
                    RDFDataMgr.write(System.out, mod.getObject(), Lang.TURTLE);
                    System.out.println("^^^^^^^^^^^^^^^^^^^  ============================");
                    SHACLForm newsf = new SHACLForm(SHACLForm.this.getId(), mod, mod.getObject().createResource(subject.getURI()), shape);
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
            .append("<div wicket:id=\"predicateObjectRepeatingView\"><label wicket:id=\"beef\"/><label wicket:id=\"beef2\"/><div wicket:id=\"childRepeatingView\"></div></div>")
            .append("</form></wicket:panel></body></html>");
        return new StringResourceStream(sb.toString());
    }
    
    public final boolean Validate() {
        Shapes shapes = Shapes.parse((new HShapes()).getShapes().getGraph());
        System.out.println("Checking file structure...");
        ValidationReport report = ShaclValidator.get().validate(shapes, mod.getObject().getGraph());
        boolean valid = report.conforms();
        if (!valid) {
            ShLib.printReport(report);
            System.out.println("========= ERROR REPORT =================");
            RDFDataMgr.write(System.out,report.getModel(), Lang.TURTLE);
            System.out.println("========================================");
            return false;
        }
        System.out.println("LIFE IS GOOD");
        return true;
    }
}