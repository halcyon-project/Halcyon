package com.ebremer.halcyon.puffin;

import com.ebremer.ethereal.RDFDetachableModel;
import java.util.ArrayList;
import java.util.UUID;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;

public class ResourceEditor extends Panel implements IMarkupResourceStreamProvider {
    private ArrayList<String> list = new ArrayList<>();
    private final RDFDetachableModel mod;

    public ResourceEditor(String id, RDFDetachableModel mod, Resource r) {
        super(id);
        this.mod = mod;
        Form form = new Form("form", mod);
        r.listProperties().forEach(s->{
            String uuid = UUID.randomUUID().toString();
            System.out.println("ADDING --> "+uuid);
            list.add(uuid);
            form.add(new PredicateObject(uuid,mod,s,null,null,null,null,null));
        });            
        form.add(new Button("saveButton") {
            @Override
            public void onSubmit() {
                System.out.println("I submit");
                RDFDataMgr.write(System.out, mod.getObject(), Lang.TURTLE);
            }}.setDefaultFormProcessing(true)
        );
        form.add(new Button("resetButton") {
            @Override
            public void onSubmit() {
                System.out.println("I reset");
                // setResponsePage(ResourceViewer.class);
            }}.setDefaultFormProcessing(false)
        );
        add(form);
    }
    
    @Override
    public IResourceStream getMarkupResourceStream(MarkupContainer container, Class<?> containerClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body><wicket:panel><form wicket:id=\"form\">")
            .append("<input type=\"submit\" wicket:id=\"saveButton\" value=\"save\" />")
            .append("<input type=\"submit\" wicket:id=\"resetButton\" value=\"reset\" />")
            .append("<table>");
        list.forEach(s->{
            sb.append("<tr><span wicket:id=\"").append(s).append("\"></span></tr>");
        });
        sb.append("</table></form></wicket:panel></body></html>");
        return new StringResourceStream(sb.toString());
    }
}

            
            /*
            TextField<String> textField = new TextField<>("lastname", Model.of(name));
            textField.add(new AjaxFormComponentUpdatingBehavior("change") {
                @Override
                    protected void onUpdate(AjaxRequestTarget target) {
                        System.out.println("Text field was modified, new value: " + textField.getModelObject());
                }
            });
            textField.setRequired(true);
            textField.setLabel(Model.of("this is cool"));
            add(textField);
            textField.add(new AjaxFormComponentUpdatingBehavior("keyup") {
                @Override
                    protected void onUpdate(AjaxRequestTarget target) {
                        System.out.println("Text field was PRESSED, new value: " + textField.getModelObject());
                }
            });
            add(textField);*/