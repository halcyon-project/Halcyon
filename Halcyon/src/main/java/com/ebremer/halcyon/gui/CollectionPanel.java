package com.ebremer.halcyon.gui;

import com.ebremer.ethereal.LDModel;
import com.ebremer.ethereal.RDFTextField;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;

/**
 *
 * @author erich
 */
public class CollectionPanel extends Form<Model> {
    
    public CollectionPanel(String name, LDModel mo) {
        super(name, new LDModel<>(mo));
        Resource s = null;
        add(new RDFTextField<String>("CollectionName", s, RDFS.label));
        add(new Button("saveButton"));
        add(new Button("resetButton") {
            @Override
            public void onSubmit() {
                setResponsePage(EditCollection.class);
            }}.setDefaultFormProcessing(false)
        );        
    }
    
    @Override
    public void onSubmit() {
        System.out.println("SAVING!");
        //Dataset ds = DatabaseLocator.getDatabase().getSecuredDataset();
        //ds.begin(ReadWrite.READ);
        //RDFDataMgr.write(System.out, ds.getNamedModel(HAL.Collections.getURI()), RDFFormat.TURTLE_PRETTY);
        //ds.end();
    }   
}
