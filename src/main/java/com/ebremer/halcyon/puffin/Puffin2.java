package com.ebremer.halcyon.puffin;

import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.ns.HAL;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.request.mapper.parameter.PageParameters;

/**
 *
 * @author erich
 */
public class Puffin2 extends BasePage {

    public Puffin2(final PageParameters parameters) {
        Model target = ModelFactory.createDefaultModel();
        Resource r = target.createResource("https://www.ebremer.com/myfirstcolorlist");
        r.addProperty(RDF.type, HAL.AnnotationClassList);
        //r.addProperty(SchemaDO.name, "My first annotation class list!");
        r.addProperty(SchemaDO.name, r.getModel().createResource("http://www.google.com"));
        //r.addProperty(SchemaDO.name, "HI!");
        r.addLiteral(FOAF.age, 54.3f);
        Form form = new Form("form");
        RDFStatement ss = new RDFStatement(r.getProperty(SchemaDO.name));
        //RDFStatement ss = new RDFStatement(r.getProperty(FOAF.age));
        form.add(new TextField("haha", ss));
        add(form);
        form.add(new AjaxButton("save") {
            @Override
            public void onSubmit(AjaxRequestTarget target) {
                System.out.println("yay");
            }}.setDefaultFormProcessing(true)
        );
    }
}
