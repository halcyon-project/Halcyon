package com.ebremer.halcyon.puffin;

import com.ebremer.halcyon.datum.DataCore;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.ns.HAL;
import com.ebremer.ns.SNO;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

public class Puffin extends BasePage  {
    private static final long serialVersionUID = 1L;
    private final DetachableModel dm;
        
    public Puffin() { 
        Model m = ModelFactory.createDefaultModel();
        dm = new DetachableModel(m);
        Dataset ds = DataCore.getInstance().getDataset();
        ds.begin(ReadWrite.READ);
        m.add(ds.getNamedModel(HAL.GroupsAndUsers));
        ds.end();
        RDFDataMgr.write(System.out, m, Lang.TURTLE);
        Model target = ModelFactory.createDefaultModel();
        Resource r = target.createResource("https://www.ebremer.com/myfirstcolorlist");
        r.addProperty(RDF.type, HAL.AnnotationClassList);
        r.addProperty(SchemaDO.name, "My first annotation class list!");
        r.addProperty(SchemaDO.name, r.getModel().createResource("http://www.google.com"));
        r.addProperty(SchemaDO.name, "HI!");
        Resource bn = target.createResource();
        bn.addProperty(HAL.hasClass, SNO.Lymphocytes).addProperty(HAL.color, "#0000ff").addProperty(RDF.type, HAL.AnnotationClass);
        r.addProperty(HAL.hasAnnotationClass, bn);
        bn = target.createResource();
        bn.addProperty(HAL.hasClass, SNO.Necrosis).addProperty(HAL.color, "#00ff00").addProperty(RDF.type, HAL.AnnotationClass);
        r.addProperty(HAL.hasAnnotationClass, bn);
        SHACLForm sf = new SHACLForm("sform", r, HAL.AnnotationClassListShape.asNode(), null);
        add(sf);
    }
}
