package com.ebremer.vandegraph.shacl;

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
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.RepeatingView;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;

/**
 * This class generates a dynamic form, designed to display and validate RDF
 * data against a SHACL shape, allowing users to interact with and modify the
 * RDF model based on the defined predicates and shapes.
 */
public class SHACLForm extends Panel implements IMarkupResourceStreamProvider {

    private final DropDownChoice<Bundle> ddc;
    private Node subject;
    private final CommandNode cn;

    public SHACLForm(String id, Resource r, Node shape, CommandNode cn) {
        super(id);
        this.cn = cn;
        DetachableResource dr = new DetachableResource(r);
        this.subject = r.asNode();
        Form form = new Form("form");
        HShapes hs = new HShapes();
        RepeatingView parentRepeatingView = new RepeatingView("prv");
        form.add(parentRepeatingView);
        ResultSet rs = hs.getPredicateFormElements(r, shape);
        WebMarkupContainer parentItem;
        while (rs.hasNext()) {
            int c = 0;
            QuerySolution qs = rs.next();
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
            predicateGroupLabel.setVisible(false);
            parentRepeatingView.add(parentItem);
            parentItem.add(predicateGroupLabel);
            parentItem.add(predicateGroupMessages);
            RepeatingView childRepeatingView = new RepeatingView("childRepeatingView");
            parentItem.add(childRepeatingView);
            Node v = Node.ANY;
            if (qs.contains("viewers")) {
                v = NodeFactory.createURI(qs.get("viewers").asLiteral().getString());
            }
            if (qs.contains("viewers") && v.equals(DASH.ValueTableViewer.asNode())) {
                GridPanel grid;
                if (qs.contains("subshapes")) {
                    String subshape = qs.get("subshapes").asLiteral().getString().split(",")[0];
                    Node na = NodeFactory.createURI(subshape);
                    grid = new GridPanel(childRepeatingView.newChildId(), r, predicate, na);
                } else {
                    grid = new GridPanel(childRepeatingView.newChildId(), r, predicate, null);
                }
                childRepeatingView.add(grid);
            } else {
                ResultSet rrs = hs.getStatementFormElements(r, predicate, shape);
                while (rrs.hasNext()) {
                    QuerySolution qqs = rrs.next();
                    Statement ma = r.getModel().asStatement(Triple.create(r.asNode(), predicate.asNode(), qqs.get("object").asNode()));
                    String messages = "";
                    if (qqs.contains("messages")) {
                        messages = qqs.get("messages").asLiteral().getString();
                    }
                    PredicateObject predicateObject = new PredicateObject(childRepeatingView.newChildId(), ma, hs, messages, shape, this, qqs, cn);
                    if (c > 0) {
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
                try {
                    hshapes.Validate(dr.getObject().getModel());
                    Component parent = SHACLForm.this.getParent();
                    SHACLForm newsf = createNewSHACLForm(dr.getObject(), subject, shape, cn);
                    SHACLForm.this.replaceWith(newsf);
                    //RDFDataMgr.write(System.out, dr.getObject().getModel(), Lang.TURTLE);
                    cn.Post(dr.getObject().getModel());
                    // Save operation completed successfully
                    target.add(parent);
                } catch (Exception e) {
                    // Log error
                    System.err.println("Error during save operation: " + e.getMessage());
                    // Add feedback to the user
                    target.appendJavaScript("alert('An error occurred during the save operation. Please try again.');");
                }
            }
        }.setDefaultFormProcessing(true));

        form.add(new AjaxButton("deleteButton") {
            @Override
            public void onSubmit(AjaxRequestTarget target) {
                System.out.println("delete this");
                //Component parent = SHACLForm.this.getParent();
                //SHACLForm newsf = new SHACLForm(SHACLForm.this.getId(), mod, mod.getObject().createResource(subject.getURI()), shape);
                //SHACLForm.this.replaceWith(newsf);
                //target.add(parent);
            }
        }.setDefaultFormProcessing(true)
        );
        form.add(new AjaxButton("resetButton") {
            @Override
            public void onSubmit(AjaxRequestTarget target) {
                HShapes hshapes = new HShapes();
                hshapes.Validate(dr.getObject().getModel());
                Component parent = SHACLForm.this.getParent();
                SHACLForm newsf = createNewSHACLForm(dr.getObject(), subject, shape, cn);
                SHACLForm.this.replaceWith(newsf);
                //RDFDataMgr.write(System.out, dr.getObject().getModel(), Lang.TURTLE);
                target.add(parent);
            }
        }.setDefaultFormProcessing(false)
        );
        add(form);
        ddc = new DropDownChoice<>("predicates", new Model<Bundle>(), hs.getStatsAndPredicates(shape, r), new BundleRender());
        ddc.setNullValid(false); // Disallow null selections
        ddc.add(new OnChangeAjaxBehavior() {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                Bundle selectedBundle = ddc.getModelObject();
                if (selectedBundle != null) {
                    // Valid selection, proceed with the logic
                    Component parent = SHACLForm.this.getParent();
                    HShapes hshapes = new HShapes();
                    Resource zz = dr.getObject().getModel().createResource(selectedBundle.getNode().getURI());
                    if (subject.isBlank()) {
                        hshapes.createProperty(shape, zz.getModel(), zz.getModel().createResource(AnonId.create(subject.getBlankNodeLabel())), zz);
                    } else {
                        hshapes.createProperty(shape, zz.getModel(), zz.getModel().createResource(subject.getURI()), zz);
                    }
                    //System.out.println("ADDED DATA =======================================");
                    //RDFDataMgr.write(System.out, dr.getObject().getModel(), Lang.TURTLE);
                    //System.out.println("^^^^^^^^^^^^^^^^^^^  ============================");
                    SHACLForm newsf = createNewSHACLForm(dr.getObject(), subject, shape, cn);
                    SHACLForm.this.replaceWith(newsf);
                    target.add(parent);
                } else {
                    // Invalid selection, show a warning
                    System.out.println("No valid selection made in DropDownChoice.");
                    target.appendJavaScript("alert('Please select a valid option.');");
                }
            }
        });

        form.add(ddc);
    }

    @Override
    public IResourceStream getMarkupResourceStream(MarkupContainer container, Class<?> containerClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("<wicket:panel>")
                .append("<style>")
                .append("form {")
                .append("    display: flex;")
                .append("    flex-direction: column;")
                .append("    width: 100%;")
                .append("    max-width: 950px;")
                .append("    margin: 20px 0;")
                .append("    padding: 20px;")
                .append("    background-color: #f9f9f9;")
                .append("    border: 1px solid #ddd;")
                .append("    border-radius: 8px;")
                .append("    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);")
                .append("}")
                .append(".button-group {")
                .append("    display: flex;")
                .append("    gap: 10px;")
                .append("    margin-bottom: 15px;")
                .append("}")
                .append("input[type='submit'] {")
                .append("    background-color: #0000ab;")
                .append("    color: white;")
                .append("    border: none;")
                .append("    padding: 8px 12px;")
                .append("    border-radius: 5px;")
                .append("    cursor: pointer;")
                .append("    font-size: 14px;")
                .append("    flex-grow: 1;")
                .append("}")
                .append("input[type='submit']:hover {")
                .append("    background-color: #000099;")
                .append("}")
                .append("select {")
                .append("    padding: 10px;")
                .append("    margin-bottom: 15px;")
                .append("    border: 1px solid #ddd;")
                .append("    border-radius: 5px;")
                .append("    font-size: 14px;")
                .append("    background-color: white;")
                .append("}")
                .append("#prv {")
                .append("    margin-top: 20px;")
                .append("    padding: 15px;")
                .append("    background-color: #eeeeee;")
                .append("    border: 1px solid #ddd;")
                .append("    border-radius: 8px;")
                .append("}")
                .append("label {")
                .append("    display: block;")
                .append("    margin-bottom: 10px;")
                .append("    font-weight: bold;")
                .append("    color: #333;")
                .append("}")
                .append("</style>")
                .append("<form wicket:id=\"form\">")
                .append("    <div class=\"button-group\">")
                .append("        <input type=\"submit\" wicket:id=\"saveButton\" value=\"Save\" />")
                .append("        <input type=\"submit\" wicket:id=\"deleteButton\" value=\"Delete\" />")
                .append("        <input type=\"submit\" wicket:id=\"resetButton\" value=\"Reset\" />")
                .append("    </div>")
                .append("    <select wicket:id=\"predicates\">Predicates</select>")
                .append("    <div id=\"prv\" wicket:id=\"prv\">")
                .append("        <label wicket:id=\"predicatename\"></label>")
                .append("        <label wicket:id=\"predicatemessages\"></label>")
                .append("        <div wicket:id=\"childRepeatingView\"></div>")
                .append("    </div>")
                .append("</form>")
                .append("</wicket:panel>");
        return new StringResourceStream(sb.toString());
    }

    private SHACLForm createNewSHACLForm(Resource resource, Node subject, Node shape, CommandNode cn) {
        if (subject.isBlank()) {
            // Recreating SHACLForm with a blank node subject
            return new SHACLForm(this.getId(), resource.getModel().createResource(AnonId.create(subject.getBlankNodeLabel())), shape, cn);
        } else {
            // Recreating SHACLForm with a URI subject
            return new SHACLForm(this.getId(), resource.getModel().createResource(subject.getURI()), shape, cn);
        }
    }

    /*
    @Override
    public String getCacheKey(MarkupContainer container, Class<?> containerClass) {
        final String classname = containerClass.isAnonymousClass() ? containerClass.getSuperclass().getSimpleName() : containerClass.getSimpleName();
        return classname + '_' + UUID.randomUUID().toString() + '_';
        //return classname + '_' + type + '_';
    }*/
}
